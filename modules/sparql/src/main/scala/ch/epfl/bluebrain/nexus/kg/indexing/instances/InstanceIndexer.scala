package ch.epfl.bluebrain.nexus.kg.indexing.instances

import cats.MonadError
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.show._
import ch.epfl.bluebrain.nexus.commons.http.JsonOps._
import ch.epfl.bluebrain.nexus.commons.iam.acls
import ch.epfl.bluebrain.nexus.commons.sparql.client.SparqlClient
import ch.epfl.bluebrain.nexus.kg.core.IndexingVocab.JsonLDKeys._
import ch.epfl.bluebrain.nexus.kg.core.IndexingVocab.PrefixMapping._
import ch.epfl.bluebrain.nexus.kg.core.Qualifier._
import ch.epfl.bluebrain.nexus.kg.core.contexts.Contexts
import ch.epfl.bluebrain.nexus.kg.core.instances.InstanceEvent._
import ch.epfl.bluebrain.nexus.kg.core.instances.attachments.Attachment
import ch.epfl.bluebrain.nexus.kg.core.instances.attachments.Attachment._
import ch.epfl.bluebrain.nexus.kg.core.instances.{InstanceEvent, InstanceId}
import ch.epfl.bluebrain.nexus.kg.core.ld.JsonLdOps._
import ch.epfl.bluebrain.nexus.kg.indexing.BaseSparqlIndexer
import ch.epfl.bluebrain.nexus.kg.indexing.query.PatchQuery
import io.circe.Json
import journal.Logger

/**
  * Instance incremental indexing logic that pushes data into an rdf triple store.
  *
  * @param client   the SPARQL client to use for communicating with the rdf triple store
  * @param contexts the context operation bundle
  * @param settings the indexing settings
  * @param F        a MonadError typeclass instance for ''F[_]''
  * @tparam F the monadic effect type
  */
class InstanceIndexer[F[_]](client: SparqlClient[F], contexts: Contexts[F], settings: InstanceIndexingSettings)(
    implicit F: MonadError[F, Throwable])
    extends BaseSparqlIndexer(settings.instanceBase, settings.nexusVocBase) {

  private val log                                              = Logger[this.type]
  private val InstanceIndexingSettings(index, base, baseNs, _) = settings

  // instance vocabulary
  private val uuidKey = "uuid".qualifyAsString

  // attachment vocabulary
  private val originalFileNameKey = "originalFileName".qualifyAsString
  private val mediaTypeKey        = "mediaType".qualifyAsString
  private val contentSizeKey      = "contentSize".qualifyAsString
  private val digestAlgoKey       = "digestAlgorithm".qualifyAsString
  private val digestKey           = "digest".qualifyAsString

  /**
    * Indexes the event by pushing it's json ld representation into the rdf triple store while also updating the
    * existing triples.
    *
    * @param event the event to index
    * @return a Unit value in the ''F[_]'' context
    */
  final def apply(event: InstanceEvent): F[Unit] = event match {
    case InstanceCreated(id, rev, m, value) =>
      log.debug(s"Indexing 'InstanceCreated' event for id '${id.show}'")
      val meta = buildMeta(id, rev, m, deprecated = false)
      contexts
        .resolve(value removeKeys ("links"))
        .map(_ deepMerge meta)
        .flatMap { json =>
          val combinedJson = buildCombined(Json.obj(createdAtTimeKey -> m.instant.jsonLd) deepMerge json, id)
          client.createGraph(index, id qualifyWith baseNs, combinedJson)
        }

    case InstanceUpdated(id, rev, m, value) =>
      log.debug(s"Indexing 'InstanceUpdated' event for id '${id.show}'")
      val meta = buildMeta(id, rev, m, deprecated = false)
      contexts
        .resolve(value removeKeys ("links"))
        .map(_ deepMerge meta)
        .flatMap { json =>
          val removeQuery = PatchQuery.inverse(id qualifyWith baseNs,
                                               createdAtTimeKey,
                                               schemaGroupKey,
                                               originalFileNameKey,
                                               mediaTypeKey,
                                               contentSizeKey,
                                               digestAlgoKey,
                                               digestKey)
          client.patchGraph(index, id qualifyWith baseNs, removeQuery, json)
        }

    case InstanceDeprecated(id, rev, m) =>
      log.debug(s"Indexing 'InstanceDeprecated' event for id '${id.show}'")
      val meta        = buildDeprecatedMeta(id, rev, m)
      val removeQuery = PatchQuery(id, id qualifyWith baseNs, revKey, deprecatedKey, updatedAtTimeKey)
      client.patchGraph(index, id qualifyWith baseNs, removeQuery, meta)

    case InstanceAttachmentCreated(id, rev, m, attachmentMeta) =>
      log.debug(s"Indexing 'InstanceAttachmentCreated' event for id '${id.show}'")
      val meta = buildAttachmentMeta(id, rev, attachmentMeta, m)
      val removeQuery = PatchQuery(id,
                                   id qualifyWith baseNs,
                                   originalFileNameKey,
                                   mediaTypeKey,
                                   contentSizeKey,
                                   digestAlgoKey,
                                   digestKey,
                                   revKey,
                                   updatedAtTimeKey)
      client.patchGraph(index, id qualifyWith baseNs, removeQuery, meta)

    case InstanceAttachmentRemoved(id, rev, m) =>
      log.debug(s"Indexing 'InstanceAttachmentRemoved' event for id '${id.show}'")
      val meta = buildRevMeta(id, rev, m)
      val removeQuery =
        PatchQuery(id,
                   id qualifyWith baseNs,
                   revKey,
                   updatedAtTimeKey,
                   originalFileNameKey,
                   mediaTypeKey,
                   contentSizeKey,
                   digestAlgoKey,
                   digestKey)
      client.patchGraph(index, id qualifyWith baseNs, removeQuery, meta)
  }

  private def buildMeta(id: InstanceId, rev: Long, meta: acls.Meta, deprecated: Boolean): Json =
    Json.obj(
      deprecatedKey -> Json.fromBoolean(deprecated),
      orgKey        -> id.schemaId.domainId.orgId.qualify.jsonLd,
      domainKey     -> id.schemaId.domainId.qualify.jsonLd,
      schemaKey     -> id.schemaId.qualify.jsonLd,
      uuidKey       -> Json.fromString(id.id),
      rdfTypeKey    -> "Instance".qualify.jsonLd
    ) deepMerge buildRevMeta(id, rev, meta)

  private def buildCombined(data: Json, id: InstanceId): Json =
    Json.obj(
      graphKey -> Json.arr(data,
                           Json.obj(
                             idKey          -> Json.fromString(id.schemaId.qualifyAsString),
                             schemaGroupKey -> id.schemaId.schemaName.qualify.jsonLd
                           )))

  private def buildDeprecatedMeta(id: InstanceId, rev: Long, meta: acls.Meta): Json =
    Json.obj(
      deprecatedKey -> Json.fromBoolean(true),
    ) deepMerge buildRevMeta(id, rev, meta)

  private def buildAttachmentMeta(id: InstanceId, rev: Long, attachmentMeta: Attachment.Meta, meta: acls.Meta): Json = {
    val Meta(_, Info(originalFileName, contentType, Size(_, size), Digest(algorithm, digest))) = attachmentMeta
    Json.obj(
      originalFileNameKey -> Json.fromString(originalFileName),
      mediaTypeKey        -> Json.fromString(contentType),
      contentSizeKey      -> Json.fromLong(size),
      digestAlgoKey       -> Json.fromString(algorithm),
      digestKey           -> Json.fromString(digest)
    ) deepMerge buildRevMeta(id, rev, meta)
  }

  private def buildRevMeta(id: InstanceId, rev: Long, meta: acls.Meta): Json =
    Json.obj(idKey            -> Json.fromString(id.qualifyAsStringWith(base)),
             updatedAtTimeKey -> meta.instant.jsonLd,
             revKey           -> Json.fromLong(rev))

}

object InstanceIndexer {

  /**
    * Constructs an instance incremental indexer that pushes data into an rdf triple store.
    *
    * @param client   the SPARQL client to use for communicating with the rdf triple store
    * @param contexts the context operation bundle
    * @param settings the indexing settings
    * @param F        a MonadError typeclass instance for ''F[_]''
    * @tparam F the monadic effect type
    */
  final def apply[F[_]](client: SparqlClient[F], contexts: Contexts[F], settings: InstanceIndexingSettings)(
      implicit F: MonadError[F, Throwable]): InstanceIndexer[F] =
    new InstanceIndexer(client, contexts, settings)
}