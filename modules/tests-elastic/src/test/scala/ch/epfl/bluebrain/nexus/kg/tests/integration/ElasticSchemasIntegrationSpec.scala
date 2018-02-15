package ch.epfl.bluebrain.nexus.kg.tests.integration

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import cats.syntax.show._
import ch.epfl.bluebrain.nexus.commons.http.RdfMediaTypes
import ch.epfl.bluebrain.nexus.commons.sparql.client.SparqlCirceSupport._
import ch.epfl.bluebrain.nexus.commons.types.search.Pagination
import ch.epfl.bluebrain.nexus.commons.types.search.QueryResult.UnscoredQueryResult
import ch.epfl.bluebrain.nexus.commons.types.search.QueryResults.UnscoredQueryResults
import ch.epfl.bluebrain.nexus.kg.core.domains.DomainId
import ch.epfl.bluebrain.nexus.kg.core.schemas.{Schema, SchemaId, SchemaRef}
import ch.epfl.bluebrain.nexus.kg.service.config.Settings.PrefixUris
import ch.epfl.bluebrain.nexus.kg.service.hateoas.Links
import ch.epfl.bluebrain.nexus.kg.service.io.PrinterSettings._
import ch.epfl.bluebrain.nexus.kg.service.query.LinksQueryResults
import ch.epfl.bluebrain.nexus.kg.service.routes.SchemaRoutes.SchemaConfig
import io.circe.Json
import io.circe.syntax._
import org.scalatest.DoNotDiscover
import org.scalatest.time.{Seconds, Span}

import scala.collection.mutable.Map
import scala.concurrent.ExecutionContextExecutor

@DoNotDiscover
class ElasticSchemasIntegrationSpec(apiUri: Uri, prefixes: PrefixUris, route: Route)(implicit
                                                                                     as: ActorSystem,
                                                                                     ec: ExecutionContextExecutor,
                                                                                     mt: ActorMaterializer)
    extends BootstrapIntegrationSpec(apiUri, prefixes) {

  import BootstrapIntegrationSpec._
  import schemaEncoders._

  "A SchemaRoutes" when {
    val idsPayload = Map[SchemaId, Schema]()

    "performing integration tests" should {

      "create schemas successfully" in {
        forAll(schemas) {
          case (schemaId, json) =>
            Put(s"/schemas/${schemaId.show}", json) ~> addCredentials(ValidCredentials) ~> route ~> check {
              idsPayload += (schemaId -> Schema(schemaId, 2L, json, false, true))
              status shouldEqual StatusCodes.Created
              responseAs[Json] shouldEqual SchemaRef(schemaId, 1L).asJson.addCoreContext
            }
        }
      }

      "publish schemas successfully" in {
        forAll(schemas) {
          case (schemaId, _) =>
            Patch(s"/schemas/${schemaId.show}/config?rev=1", SchemaConfig(published = true)) ~> addCredentials(
              ValidCredentials) ~> route ~> check {
              status shouldEqual StatusCodes.OK
              responseAs[Json] shouldEqual SchemaRef(schemaId, 2L).asJson.addCoreContext
            }
        }
      }

      "list all schemas" in {
        eventually(timeout(Span(indexTimeout, Seconds)), interval(Span(1, Seconds))) {
          Get(s"/schemas") ~> addCredentials(ValidCredentials) ~> route ~> check {
            status shouldEqual StatusCodes.OK
            contentType shouldEqual RdfMediaTypes.`application/ld+json`.toContentType
            val expectedResults = UnscoredQueryResults(schemas.length.toLong, schemas.take(10).map {
              case (schemaId, _) => UnscoredQueryResult(schemaId)
            })
            val expectedLinks = Links("@context" -> s"${prefixes.LinksContext}",
                                      "self" -> s"$apiUri/schemas",
                                      "next" -> s"$apiUri/schemas?from=10&size=10")
            responseAs[Json] shouldEqual LinksQueryResults(expectedResults, expectedLinks).asJson.addSearchContext
          }
        }
      }

      "list schemas on organization rand with pagination retrieve all fields" in {
        val pagination = Pagination(0L, 5)
        val path       = s"/schemas/rand?size=${pagination.size}&fields=all"
        eventually(timeout(Span(indexTimeout, Seconds)), interval(Span(1, Seconds))) {
          Get(path) ~> addCredentials(ValidCredentials) ~> route ~> check {
            status shouldEqual StatusCodes.OK
            contentType shouldEqual RdfMediaTypes.`application/ld+json`.toContentType
            val expectedResults = UnscoredQueryResults(
              (schemas.length - 3 * 5).toLong,
              schemas
                .collect { case (schemaId @ SchemaId(DomainId(orgId, _), _, _), _) if orgId.id == "rand" => schemaId }
                .map(id => UnscoredQueryResult(idsPayload(id)))
                .take(pagination.size)
            )
            val expectedLinks = Links("@context" -> s"${prefixes.LinksContext}",
                                      "self" -> s"$apiUri$path",
                                      "next" -> s"$apiUri$path&from=5")
            responseAs[Json] shouldEqual LinksQueryResults(expectedResults, expectedLinks).asJson.addSearchContext
          }
        }
      }

      "output the correct total even when the from query parameter is out of scope" in {
        val pagination = Pagination(0L, 5)
        val path       = s"/schemas/rand?size=${pagination.size}&from=100"
        eventually(timeout(Span(indexTimeout, Seconds)), interval(Span(1, Seconds))) {
          Get(path) ~> addCredentials(ValidCredentials) ~> route ~> check {
            status shouldEqual StatusCodes.OK
            contentType shouldEqual RdfMediaTypes.`application/ld+json`.toContentType
            val expectedResults =
              UnscoredQueryResults((schemas.length - 3 * 5).toLong, List.empty[UnscoredQueryResult[SchemaId]])
            val expectedLinks =
              Links("@context" -> s"${prefixes.LinksContext}",
                    "self"     -> s"$apiUri$path",
                    "previous" -> s"$apiUri$path".replace("from=100", s"from=${(schemas.length - 3 * 5) - 5}"))
            responseAs[Json] shouldEqual LinksQueryResults(expectedResults, expectedLinks).asJson.addSearchContext
          }
        }
      }

      "list schemas on organization nexus and domain development" in {
        val path = s"/schemas/nexus/development"
        Get(path) ~> addCredentials(ValidCredentials) ~> route ~> check {
          status shouldEqual StatusCodes.OK
          val expectedResults =
            UnscoredQueryResults(3L, schemas.take(3).map { case (schemaId, _) => UnscoredQueryResult(schemaId) })
          val expectedLinks = Links("@context" -> s"${prefixes.LinksContext}", "self" -> Uri(s"$apiUri$path"))
          responseAs[Json] shouldEqual LinksQueryResults(expectedResults, expectedLinks).asJson.addSearchContext
        }
      }

      "list schemas on organization nexus and domain development and specific schema name" in {
        val (schemaId, _) = schemas.head
        val path          = s"/schemas/${schemaId.schemaName.show}"
        Get(path) ~> addCredentials(ValidCredentials) ~> route ~> check {
          status shouldEqual StatusCodes.OK
          val expectedResults =
            UnscoredQueryResults(3L, schemas.take(3).map { case (id, _) => UnscoredQueryResult(id) })
          val expectedLinks = Links("@context" -> s"${prefixes.LinksContext}", "self" -> Uri(s"$apiUri$path"))
          responseAs[Json] shouldEqual LinksQueryResults(expectedResults, expectedLinks).asJson.addSearchContext
        }
      }

      "deprecate one schemas on nexus organization" in {
        val (schemaId, _) = schemas.head
        Delete(s"/schemas/${schemaId.show}?rev=2") ~> addCredentials(ValidCredentials) ~> route ~> check {
          status shouldEqual StatusCodes.OK
          responseAs[Json] shouldEqual SchemaRef(schemaId, 3L).asJson.addCoreContext
        }
      }

      "list schemas on organizations rand and deprecated" in {
        eventually(timeout(Span(indexTimeout, Seconds)), interval(Span(1, Seconds))) {
          val (schemaId, _) = schemas.head
          val path          = s"/schemas/${schemaId.schemaName.show}?deprecated=false"
          Get(path) ~> addCredentials(ValidCredentials) ~> route ~> check {
            status shouldEqual StatusCodes.OK
            val expectedResults =
              UnscoredQueryResults(2L, schemas.slice(1, 3).map { case (id, _) => UnscoredQueryResult(id) })
            val expectedLinks = Links("@context" -> s"${prefixes.LinksContext}", "self" -> Uri(s"$apiUri$path"))
            responseAs[Json] shouldEqual LinksQueryResults(expectedResults, expectedLinks).asJson.addSearchContext
          }
        }
      }

    }
  }
}