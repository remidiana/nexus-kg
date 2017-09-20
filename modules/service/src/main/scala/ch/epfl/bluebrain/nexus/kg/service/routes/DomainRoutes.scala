package ch.epfl.bluebrain.nexus.kg.service.routes

import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import cats.instances.future._
import ch.epfl.bluebrain.nexus.commons.sparql.client.SparqlCirceSupport._
import ch.epfl.bluebrain.nexus.commons.sparql.client.SparqlClient
import ch.epfl.bluebrain.nexus.kg.core.domains.{Domain, DomainId, DomainRef, Domains}
import ch.epfl.bluebrain.nexus.kg.core.organizations.OrgId
import ch.epfl.bluebrain.nexus.kg.indexing.Qualifier._
import ch.epfl.bluebrain.nexus.kg.indexing.filtering.FilteringSettings
import ch.epfl.bluebrain.nexus.kg.indexing.query.builder.FilterQueries
import ch.epfl.bluebrain.nexus.kg.indexing.query.builder.FilterQueries._
import ch.epfl.bluebrain.nexus.kg.indexing.query.{QuerySettings, SparqlQuery}
import ch.epfl.bluebrain.nexus.kg.service.directives.QueryDirectives._
import ch.epfl.bluebrain.nexus.kg.service.io.PrinterSettings._
import ch.epfl.bluebrain.nexus.kg.service.io.RoutesEncoder
import ch.epfl.bluebrain.nexus.kg.service.routes.DomainRoutes.DomainDescription
import ch.epfl.bluebrain.nexus.kg.service.routes.SearchResponse._
import io.circe.generic.auto._
import io.circe.{Encoder, Json}
import kamon.akka.http.KamonTraceDirectives._

import scala.concurrent.{ExecutionContext, Future}

/**
  * Http route definitions for domain specific functionality.
  *
  * @param domains           the domain operation bundle
  * @param domainQueries     query builder for domains
  * @param base              the service public uri + prefix
  * @param querySettings     query parameters from settings
  * @param filteringSettings filtering parameters from settings
  */
final class DomainRoutes(
  domains: Domains[Future],
  domainQueries: FilterQueries[Future, DomainId],
  base: Uri)(implicit querySettings: QuerySettings, filteringSettings: FilteringSettings) {

  private val encoders = new DomainCustomEncoders(base)
  import encoders._

  def routes: Route = handleExceptions(ExceptionHandling.exceptionHandler) {
    handleRejections(RejectionHandling.rejectionHandler) {
      pathPrefix("organizations" / Segment / "domains") { orgIdString =>
        val orgId = OrgId(orgIdString)
        (pathEndOrSingleSlash & get & searchQueryParams) { (pagination, filterOpt, termOpt, deprecatedOpt) =>
          traceName("searchDomains") {
            val filter = filterFrom(deprecatedOpt, filterOpt, querySettings.nexusVocBase)
            domainQueries.list(orgId, filter, pagination, termOpt).buildResponse(base, pagination)
          }
        } ~
        pathPrefix(Segment) { id =>
          val domainId = DomainId(orgId, id)
          pathEnd {
            (put & entity(as[DomainDescription])) { desc =>
              traceName("createDomain") {
                onSuccess(domains.create(domainId, desc.description)) { ref =>
                  complete(StatusCodes.Created -> ref)
                }
              }
            } ~
            get {
              traceName("getDomain") {
                onSuccess(domains.fetch(domainId)) {
                  case Some(domain) => complete(domain)
                  case None         => complete(StatusCodes.NotFound)
                }
              }
            } ~
            delete {
              parameter('rev.as[Long]) { rev =>
                traceName("deprecateDomain") {
                  onSuccess(domains.deprecate(domainId, rev)) { ref =>
                    complete(StatusCodes.OK -> ref)
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

object DomainRoutes {

  /**
    * Local data type that wraps a textual description of a domain.
    *
    * @param description a domain description
    */
  final case class DomainDescription(description: String)

  /**
    * Constructs a new ''DomainRoutes'' instance that defines the http routes specific to domains.
    *
    * @param domains       the domain operation bundle
    * @param client        the sparql client
    * @param querySettings query parameters form settings
    * @param base          the service public uri + prefix
    * @return a new ''DomainRoutes'' instance
    */
  final def apply(domains: Domains[Future], client: SparqlClient[Future], querySettings: QuerySettings, base: Uri)(implicit
    ec: ExecutionContext, filteringSettings: FilteringSettings): DomainRoutes = {
    implicit val qs: QuerySettings = querySettings
    val domainQueries = FilterQueries[Future, DomainId](SparqlQuery[Future](client), querySettings)
    new DomainRoutes(domains, domainQueries, base)
  }
}

private class DomainCustomEncoders(base: Uri) extends RoutesEncoder[DomainId, DomainRef](base){

  implicit def domainEncoder: Encoder[Domain] = Encoder.encodeJson.contramap { domain =>
    refEncoder.apply(DomainRef(domain.id, domain.rev)).deepMerge(Json.obj(
      "deprecated" -> Json.fromBoolean(domain.deprecated),
      "description" -> Json.fromString(domain.description)
    ))
  }
}