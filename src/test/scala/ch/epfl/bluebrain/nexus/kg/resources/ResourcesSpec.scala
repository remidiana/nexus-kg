package ch.epfl.bluebrain.nexus.kg.resources

import java.time.{Clock, Instant, ZoneId}
import java.util.UUID

import cats.data.EitherT
import cats.syntax.show._
import cats.{Id => CId}
import ch.epfl.bluebrain.nexus.commons.http.syntax.circe._
import ch.epfl.bluebrain.nexus.commons.test
import ch.epfl.bluebrain.nexus.commons.test.Randomness
import ch.epfl.bluebrain.nexus.iam.client.types.Identity
import ch.epfl.bluebrain.nexus.iam.client.types.Identity.Anonymous
import ch.epfl.bluebrain.nexus.kg.async.DistributedCache
import ch.epfl.bluebrain.nexus.kg.config.Contexts._
import ch.epfl.bluebrain.nexus.kg.config.Schemas._
import ch.epfl.bluebrain.nexus.kg.config.Vocabulary._
import ch.epfl.bluebrain.nexus.kg.config.{AppConfig, Settings}
import ch.epfl.bluebrain.nexus.kg.resolve.{ProjectResolution, Resolver, StaticResolution}
import ch.epfl.bluebrain.nexus.kg.resources.Ref.Latest
import ch.epfl.bluebrain.nexus.kg.resources.Rejection._
import ch.epfl.bluebrain.nexus.kg.resources.ResourcesSpec._
import ch.epfl.bluebrain.nexus.kg.resources.State.Initial
import ch.epfl.bluebrain.nexus.kg.resources.attachment.Attachment.{BinaryDescription, Digest, Size, StoredSummary}
import ch.epfl.bluebrain.nexus.kg.resources.attachment.AttachmentStore
import ch.epfl.bluebrain.nexus.rdf.Iri
import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri
import ch.epfl.bluebrain.nexus.rdf.Vocabulary._
import ch.epfl.bluebrain.nexus.rdf.syntax.circe.context._
import ch.epfl.bluebrain.nexus.sourcing.mem.MemoryAggregate
import ch.epfl.bluebrain.nexus.sourcing.mem.MemoryAggregate._
import com.typesafe.config.ConfigFactory
import io.circe.Json
import org.mockito.ArgumentMatchers.isA
import org.mockito.Mockito
import org.mockito.Mockito._
import org.scalatest._
import org.scalatest.mockito.MockitoSugar.{mock => mmock}

class ResourcesSpec
    extends WordSpecLike
    with Matchers
    with OptionValues
    with EitherValues
    with Randomness
    with BeforeAndAfter
    with test.Resources {

  private implicit val appConfig        = new Settings(ConfigFactory.parseResources("app.conf").resolve()).appConfig
  private implicit val clock: Clock     = Clock.fixed(Instant.ofEpochSecond(3600), ZoneId.systemDefault())
  private val agg                       = MemoryAggregate("resources")(Initial, Repo.next, Repo.eval).toF[CId]
  private implicit val repo             = Repo(agg, clock)
  private implicit val store            = mmock[AttachmentStore[CId, String, String]]
  private implicit val resolution       = staticResolution()
  private val resources: Resources[CId] = Resources[CId]

  private def uuid = UUID.randomUUID().toString.toLowerCase

  private def randomIri() = Iri.absolute(s"http://example.com/$uuid").right.value

  before {
    Mockito.reset(store)
  }

  trait Context {
    implicit val identity: Identity = Anonymous
    val projectRef                  = ProjectRef(uuid)
    val base                        = Iri.absolute(s"http://example.com/base").right.value
    val schema                      = Latest(crossResolverSchemaUri)
    val id                          = Iri.absolute(s"http://example.com/$uuid").right.value
    val resId                       = Id(projectRef, id)
    val resolver = jsonContentOf("/resolve/cross-project.json") addContext resolverCtxUri deepMerge Json.obj(
      "@id" -> Json.fromString(id.show))

    val resolverUpdated = jsonContentOf("/resolve/cross-project-updated.json") addContext resolverCtxUri deepMerge Json
      .obj("@id" -> Json.fromString(id.show))
    val types = Set[AbsoluteIri](nxv.Resolver, nxv.CrossProject)
  }

  trait Attachment extends Context {
    val desc       = BinaryDescription("name", "text/plain")
    val source     = "some text"
    val relative   = Iri.relative("./other").right.value
    val attributes = desc.process(StoredSummary(relative, Size(value = 20L), Digest("MD5", "1234")))
    when(store.save(resId, desc, source)).thenReturn(EitherT.rightT[CId, Rejection](attributes))
    when(store.save(resId, desc, source)).thenReturn(EitherT.rightT[CId, Rejection](attributes))
  }

  "A Resources bundle" when {

    "performing create operations" should {

      "create a new resource validated against empty schema (resource schema) with a payload only containing @id and @context" in new Context {
        private val schemaRef = Ref(resourceSchemaUri)
        private val genId     = randomIri()
        private val genRes    = Id(projectRef, genId)
        private val json =
          Json.obj("@context" -> Json.obj("nxv" -> Json.fromString(nxv.base)), "@id" -> Json.fromString(genId.show))
        resources.create(projectRef, base, schemaRef, json).value.right.value shouldEqual
          ResourceF.simpleF(genRes, json, schema = schemaRef)
      }

      "create a new resource validated against empty schema (resource schema) with a payload only containing @context" in new Context {
        private val schemaRef = Ref(resourceSchemaUri)
        val json              = Json.obj("@context" -> Json.obj("nxv" -> Json.fromString(nxv.base)))
        private val resource  = resources.create(projectRef, base, schemaRef, json).value.right.value
        resource shouldEqual ResourceF.simpleF(Id(projectRef, resource.id.value), json, schema = schemaRef)
      }

      "create a new resource validated against the resolver schema without passing the id on the call (but provided on the Json)" in new Context {
        resources.create(projectRef, base, schema, resolver).value.right.value shouldEqual
          ResourceF.simpleF(resId, resolver, schema = schema, types = types)
      }

      "create a new resource validated against the resolver schema without passing the id on the call (neither on the Json)" in new Context {
        private val resolverNoId = resolver removeKeys "@id"
        private val result       = resources.create(projectRef, base, schema, resolverNoId).value.right.value
        private val generatedId  = result.id.copy(parent = projectRef)
        result.id.value.show should startWith(base.show)
        result shouldEqual
          ResourceF.simpleF(generatedId, resolverNoId, schema = schema, types = types)
      }

      "create a new resource validated against the resolver schema passing the id on the call (the provided on the Json)" in new Context {
        resources.create(resId, schema, resolver).value.right.value shouldEqual
          ResourceF.simpleF(resId, resolver, schema = schema, types = types)
      }

      "prevent to create a resource that does not validate" in new Context {
        private val wrongJson = jsonContentOf("/resolve/cross-project-wrong.json")
        resources.create(projectRef, base, schema, wrongJson).value.left.value shouldBe a[InvalidResource]
      }

      "prevent to create a resource with non existing schema" in new Context {
        private val refSchema = Ref(randomIri())
        resources.create(projectRef, base, refSchema, resolver).value.left.value shouldEqual NotFound(refSchema)
      }

      "prevent to create a resource with wrong context value" in new Context {
        private val json = resolver deepMerge Json.obj(
          "@context" -> Json.arr(Json.fromString(resolverCtxUri.show), Json.fromInt(3)))
        resources.create(projectRef, base, schema, json).value.left.value shouldEqual IllegalContextValue(List.empty)
      }

      "prevent to create a resource with wrong context that cannot be resolved" in new Context {
        private val notFoundIri = randomIri()
        private val json        = resolver removeKeys "@context" addContext resolverCtxUri addContext notFoundIri
        resources.create(projectRef, base, schema, json).value.left.value shouldEqual NotFound(Ref(notFoundIri))
      }

      "prevent creating a schema which doesn't have nxv:Schema type" in new Context {
        private val schemaRef = Ref(shaclSchemaUri)
        val json = jsonContentOf("/schemas/cross-project-resolver.json").mapObject(
          _.add("@type", Json.fromString("nxv:Resource")))

        val resourceId =
          Id(projectRef, Iri.absolute("https://bluebrain.github.io/nexus/schemas/cross-project-resolver").right.value)
        resources.create(resourceId, schemaRef, json).value.left.value shouldEqual IncorrectTypes(
          resourceId.ref,
          Set(nxv.Resource.value))
      }

      "use correct ID when creating a schema" in new Context {
        private val schemaRef = Ref(shaclSchemaUri)
        val json              = jsonContentOf("/schemas/cross-project-resolver.json")
        val resourceId =
          Id(projectRef, Iri.absolute("https://bluebrain.github.io/nexus/schemas/cross-project-resolver").right.value)
        private val resource = resources.create(resourceId, schemaRef, json).value.right.value
        resource shouldEqual ResourceF.simpleF(Id(projectRef, resource.id.value),
                                               json,
                                               types = Set(nxv.Schema.value),
                                               schema = schemaRef)
      }
    }

    "performing update operations" should {

      "update a resource (resolver)" in new Context {
        resources.create(projectRef, base, schema, resolver).value.right.value shouldBe a[Resource]
        resources.update(resId, 1L, None, resolverUpdated).value.right.value shouldEqual
          ResourceF.simpleF(resId, resolverUpdated, 2L, schema = schema, types = types)
      }

      "update a resource (resolver) when the provided schema matches the created schema" in new Context {
        resources.create(projectRef, base, schema, resolver).value.right.value shouldBe a[Resource]
        resources.update(resId, 1L, Some(schema), resolverUpdated).value.right.value shouldEqual
          ResourceF.simpleF(resId, resolverUpdated, 2L, schema = schema, types = types)
      }

      "prevent to update a resource (resolver) when the provided schema does not match the created schema" in new Context {
        resources.create(projectRef, base, schema, resolver).value.right.value shouldBe a[Resource]
        private val schemaRef = Ref(randomIri())
        resources.update(resId, 1L, Some(schemaRef), resolverUpdated).value.left.value shouldEqual NotFound(schemaRef)
      }

      "prevent to update a resource (resolver) that does not exists" in new Context {
        resources.update(resId, 1L, Some(schema), resolverUpdated).value.left.value shouldEqual NotFound(resId.ref)
      }
    }

    "performing tag operations" should {
      val tag = Json.obj("tag" -> Json.fromString("some"), "rev" -> Json.fromLong(1L))

      "tag a resource" in new Context {
        resources.create(projectRef, base, schema, resolver).value.right.value shouldBe a[Resource]
        resources.update(resId, 1L, None, resolverUpdated).value.right.value shouldBe a[Resource]
        resources.tag(resId, 2L, None, tag).value.right.value shouldEqual
          ResourceF
            .simpleF(resId, resolverUpdated, 3L, schema = schema, types = types)
            .copy(tags = Map("some" -> 1L))
      }

      "prevent tagging a resource with incorrect payload" in new Context {
        resources.create(projectRef, base, schema, resolver).value.right.value shouldBe a[Resource]
        resources.update(resId, 1L, None, resolverUpdated).value.right.value shouldBe a[Resource]
        private val invalidPayload: Json = Json.obj("a" -> Json.fromString("b"))
        resources.tag(resId, 2L, None, invalidPayload).value.left.value shouldBe a[InvalidPayload]
      }

      "prevent tagging a resource when the provided schema does not match the created schema" in new Context {
        resources.create(projectRef, base, schema, resolver).value.right.value shouldBe a[Resource]
        private val schemaRef = Ref(randomIri())
        resources.tag(resId, 1L, Some(schemaRef), tag).value.left.value shouldEqual NotFound(schemaRef)
      }
    }

    "performing deprecate operations" should {
      "deprecate a resource" in new Context {
        resources.create(projectRef, base, schema, resolver).value.right.value shouldBe a[Resource]
        resources.deprecate(resId, 1L, None).value.right.value shouldEqual
          ResourceF.simpleF(resId, resolver, 2L, schema = schema, types = types, deprecated = true)
      }

      "prevent deprecating a resource when the provided schema does not match the created schema" in new Context {
        resources.create(projectRef, base, schema, resolver).value.right.value shouldBe a[Resource]
        private val schemaRef = Ref(randomIri())
        resources.deprecate(resId, 1L, Some(schemaRef)).value.left.value shouldEqual NotFound(schemaRef)
      }
    }

    "performing add attachment operations" should {
      "add an attachment to a resource" in new Attachment {
        resources.create(projectRef, base, schema, resolver).value.right.value shouldBe a[Resource]
        resources.attach(resId, 1L, None, desc, source).value.right.value shouldEqual
          ResourceF.simpleF(resId, resolver, 2L, schema = schema, types = types).copy(attachments = Set(attributes))
      }

      "prevent adding an attachment to a resource when the provided schema does not match the created schema" in new Attachment {
        resources.create(projectRef, base, schema, resolver).value.right.value shouldBe a[Resource]
        private val schemaRef = Ref(randomIri())
        resources.attach(resId, 1L, Some(schemaRef), desc, source).value.left.value shouldEqual NotFound(schemaRef)
      }
    }

    "performing remove attachments operations" should {
      "remove an attachment from a resource" in new Attachment {
        resources.create(projectRef, base, schema, resolver).value.right.value shouldBe a[Resource]
        resources.attach(resId, 1L, None, desc, source).value.right.value shouldBe a[Resource]
        resources.unattach(resId, 2L, None, "name").value.right.value shouldBe a[Resource]
      }

      "prevent removing an attachment from resource when the provided schema does not match the created schema" in new Attachment {
        resources.create(projectRef, base, schema, resolver).value.right.value shouldBe a[Resource]
        resources.attach(resId, 1L, None, desc, source).value.right.value shouldBe a[Resource]
        private val schemaRef = Ref(randomIri())
        resources.unattach(resId, 2L, Some(schemaRef), "name").value.left.value shouldEqual NotFound(schemaRef)
      }
    }

    "performing fetch operations" should {

      "return a resource" in new Context {
        resources.create(projectRef, base, schema, resolver).value.right.value shouldBe a[Resource]
        resources.fetch(resId, None).value.value shouldEqual
          ResourceF.simpleF(resId, resolver, schema = schema, types = types)
      }

      "return a specific revision of the resource" in new Context {
        resources.create(projectRef, base, schema, resolver).value.right.value shouldBe a[Resource]
        resources.fetch(resId, None).value.value shouldEqual
          ResourceF.simpleF(resId, resolver, schema = schema, types = types)
      }

      "return the requested resource on a specific revision" in new Context {
        resources.create(projectRef, base, schema, resolver).value.right.value shouldBe a[Resource]
        resources.update(resId, 1L, None, resolverUpdated).value.right.value shouldBe a[Resource]
        resources.fetch(resId, 2L, None).value.value shouldEqual
          ResourceF.simpleF(resId, resolverUpdated, 2L, schema = schema, types = types)
        resources.fetch(resId, 1L, None).value.value shouldEqual
          ResourceF.simpleF(resId, resolver, schema = schema, types = types)
        resources.fetch(resId, 2L, None).value.value shouldEqual resources.fetch(resId, None).value.value
      }

      "return the requested resource on a specific tag" in new Context {
        private val tag = Json.obj("tag" -> Json.fromString("some"), "rev" -> Json.fromLong(2L))
        resources.create(projectRef, base, schema, resolver).value.right.value shouldBe a[Resource]
        resources.update(resId, 1L, None, resolverUpdated).value.right.value shouldBe a[Resource]
        resources.tag(resId, 2L, None, tag).value.right.value shouldBe a[Resource]
        resources.fetch(resId, "some", None).value.value shouldEqual
          ResourceF.simpleF(resId, resolverUpdated, 2L, schema = schema, types = types)
        resources.fetch(resId, "some", None).value.value shouldEqual resources.fetch(resId, 2L, None).value.value
      }

      "return None when the provided schema does not match the created schema" in new Context {
        resources.create(projectRef, base, schema, resolver).value.right.value shouldBe a[Resource]
        private val schemaRef = Ref(randomIri())
        resources.fetch(resId, Some(schemaRef)).value shouldEqual None
      }
    }

    "performing materialize operations" should {
      "materialize a resource" in new Context {
        private val resource     = resources.create(projectRef, base, schema, resolver).value.right.value
        private val materialized = resources.materialize(resource).value.right.value
        materialized.value.source shouldEqual resolver
        materialized.value.ctx shouldEqual resolverCtx.contextValue
      }
    }
  }
}

object ResourcesSpec {
  private val cache: DistributedCache[CId] = mmock[DistributedCache[CId]]
  when(cache.resolvers(isA(classOf[ProjectRef]))).thenReturn(Set.empty[Resolver])
  private[resources] def staticResolution(): ProjectResolution[CId] =
    new ProjectResolution[CId](cache, StaticResolution(AppConfig.iriResolution)) {}
}