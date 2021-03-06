package ch.epfl.bluebrain.nexus.kg.directives

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import cats.syntax.show._
import ch.epfl.bluebrain.nexus.admin.client.AdminClient
import ch.epfl.bluebrain.nexus.admin.client.types.Project
import ch.epfl.bluebrain.nexus.commons.types.HttpRejection.UnauthorizedAccess
import ch.epfl.bluebrain.nexus.iam.client.types.AuthToken
import ch.epfl.bluebrain.nexus.kg.Error
import ch.epfl.bluebrain.nexus.kg.Error._
import ch.epfl.bluebrain.nexus.kg.async.DistributedCache
import ch.epfl.bluebrain.nexus.kg.config.Vocabulary._
import ch.epfl.bluebrain.nexus.kg.directives.ProjectDirectives._
import ch.epfl.bluebrain.nexus.kg.marshallers.RejectionHandling
import ch.epfl.bluebrain.nexus.kg.marshallers.instances._
import ch.epfl.bluebrain.nexus.kg.resources.ProjectLabel
import ch.epfl.bluebrain.nexus.kg.resources.Rejection.{DownstreamServiceError, ProjectNotFound}
import ch.epfl.bluebrain.nexus.rdf.Iri
import ch.epfl.bluebrain.nexus.rdf.Iri.AbsoluteIri
import ch.epfl.bluebrain.nexus.rdf.Vocabulary._
import io.circe.generic.auto._
import io.circe.{Decoder, Encoder, Json}
import monix.eval.Task
import org.mockito.Mockito
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfter, EitherValues, Matchers, WordSpecLike}

class ProjectDirectivesSpec
    extends WordSpecLike
    with Matchers
    with EitherValues
    with MockitoSugar
    with BeforeAndAfter
    with ScalatestRouteTest {

  private implicit val cache                   = mock[DistributedCache[Task]]
  private implicit val client                  = mock[AdminClient[Task]]
  private implicit val cred: Option[AuthToken] = None

  private implicit val iriEncoder: Encoder[AbsoluteIri] = Encoder.encodeString.contramap(_.show)
  private implicit val iriDecoder: Decoder[AbsoluteIri] = Decoder.decodeString.map(Iri.absolute(_).right.value)

  private case class PrefixMapping(prefix: String, namespace: AbsoluteIri)
  private implicit val pmDecoder: Decoder[PrefixMapping] =
    Decoder.forProduct2[String, AbsoluteIri, PrefixMapping]("prefix", "namespace") {
      case (prefix, namespace) => PrefixMapping(prefix, namespace)
    }
  private implicit val pmEncoder: Encoder[Map[String, AbsoluteIri]] = Encoder.encodeJson.contramap { pm =>
    Json.arr(pm.toList.map {
      case (k, v) => Json.obj("prefix" -> Json.fromString(k), "namespace" -> Json.fromString(v.toString))
    }: _*)
  }

  private implicit def projectDecoder: Decoder[Project] =
    Decoder.forProduct7[String, String, List[PrefixMapping], AbsoluteIri, Long, Boolean, String, Project](
      "name",
      "label",
      "prefixMappings",
      "base",
      "rev",
      "deprecated",
      "uuid") {
      case (name, label, pm, base, rev, deprecated, uuid) =>
        val prefixMappings = pm.map(e => e.prefix -> e.namespace).toMap
        prefixMappings("nxv") shouldEqual nxv.base
        Project(name, label, prefixMappings - "nxv", base, rev, deprecated, uuid)
    }

  before {
    Mockito.reset(cache)
    Mockito.reset(client)
  }

  "A Project directives" should {

    def projectRoute(): Route = {
      import monix.execution.Scheduler.Implicits.global
      handleRejections(RejectionHandling()) {
        (get & project) { project =>
          complete(StatusCodes.OK -> project)
        }
      }
    }

    "fetch the project from the cache" in {
      val label   = ProjectLabel("account", "project")
      val project = Project("name", "project", Map.empty, nxv.projects, 1L, false, "uuid")
      when(cache.project(label)).thenReturn(Task.pure(Some(project): Option[Project]))

      Get("/account/project") ~> projectRoute() ~> check {
        responseAs[LabeledProject] shouldEqual LabeledProject(label, project)
      }
    }

    "fetch the project from admin client when not present on the cache" in {
      val label   = ProjectLabel("account", "project")
      val project = Project("name", "project", Map.empty, nxv.projects, 1L, false, "uuid")
      when(cache.project(label)).thenReturn(Task.pure(None: Option[Project]))
      when(client.getProject("account", "project")).thenReturn(Task.pure(Some(project): Option[Project]))

      Get("/account/project") ~> projectRoute() ~> check {
        responseAs[LabeledProject] shouldEqual LabeledProject(label, project)
      }
    }

    "fetch the project from admin client when cache throws an error" in {
      val label   = ProjectLabel("account", "project")
      val project = Project("name", "project", Map.empty, nxv.projects, 1L, false, "uuid")
      when(cache.project(label)).thenReturn(Task.raiseError(new RuntimeException))
      when(client.getProject("account", "project")).thenReturn(Task.pure(Some(project): Option[Project]))

      Get("/account/project") ~> projectRoute() ~> check {
        responseAs[LabeledProject] shouldEqual LabeledProject(label, project)
      }
    }

    "reject when not found neither in the cache nor doing IAM call" in {
      val label = ProjectLabel("account", "project")
      when(cache.project(label)).thenReturn(Task.pure(None: Option[Project]))
      when(client.getProject("account", "project")).thenReturn(Task.pure(None: Option[Project]))

      Get("/account/project") ~> projectRoute() ~> check {
        status shouldEqual StatusCodes.NotFound
        responseAs[Error].code shouldEqual classNameOf[ProjectNotFound.type]
      }
    }

    "reject when IAM signals UnauthorizedAccess" in {
      val label = ProjectLabel("account", "project")
      when(cache.project(label)).thenReturn(Task.pure(None: Option[Project]))
      when(client.getProject("account", "project")).thenReturn(Task.raiseError(UnauthorizedAccess))

      Get("/account/project") ~> projectRoute() ~> check {
        status shouldEqual StatusCodes.Unauthorized
        responseAs[Error].code shouldEqual classNameOf[UnauthorizedAccess.type]
      }
    }

    "reject when IAM signals another error" in {
      val label = ProjectLabel("account", "project")
      when(cache.project(label)).thenReturn(Task.pure(None: Option[Project]))
      when(client.getProject("account", "project"))
        .thenReturn(Task.raiseError(new RuntimeException("Something went wrong")))

      Get("/account/project") ~> projectRoute() ~> check {
        status shouldEqual StatusCodes.BadGateway
        responseAs[Error].code shouldEqual classNameOf[DownstreamServiceError.type]
      }
    }
  }

}
