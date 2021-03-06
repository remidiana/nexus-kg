package ch.epfl.bluebrain.nexus.kg.config

import ch.epfl.bluebrain.nexus.kg.config.AppConfig._
import ch.epfl.bluebrain.nexus.rdf.Iri
import com.typesafe.config.ConfigFactory
import org.scalatest.{EitherValues, Matchers, WordSpecLike}

import scala.concurrent.duration._

class AppConfigSpec extends WordSpecLike with Matchers with EitherValues {

  "An AppConfig" should {
    val valid = ConfigFactory.parseResources("app.conf").resolve()

    "provide the appropriate config" in {
      implicit val appConfig = new Settings(valid).appConfig

      appConfig.description shouldEqual Description("kg")
      appConfig.http shouldEqual HttpConfig("127.0.0.1", 8080, "v1", "http://127.0.0.1:8080")
      appConfig.cluster shouldEqual ClusterConfig(2.seconds, 5.seconds, 30, None)
      appConfig.persistence shouldEqual PersistenceConfig("cassandra-journal",
                                                          "cassandra-snapshot-store",
                                                          "cassandra-query-journal")
      appConfig.attachments shouldEqual AttachmentsConfig(Iri.absolute("file:///tmp").right.value, "SHA-256")
      appConfig.iam shouldEqual IamConfig("http://localhost:8080/iam")
      appConfig.sparql shouldEqual SparqlConfig("http://localhost:9999/bigdata", None, None, "kg")
      appConfig.elastic shouldEqual ElasticConfig("http://localhost:9200", "kg", "doc", "kg_default")
      appConfig.pagination shouldEqual PaginationConfig(0L, 20, 100)
      appConfig.kafka shouldEqual KafkaConfig("organization", "project")

      implicitly[SparqlConfig] shouldEqual SparqlConfig("http://localhost:9999/bigdata", None, None, "kg")
      implicitly[ElasticConfig] shouldEqual ElasticConfig("http://localhost:9200", "kg", "doc", "kg_default")
      implicitly[PaginationConfig] shouldEqual PaginationConfig(0L, 20, 100)
      implicitly[PersistenceConfig] shouldEqual PersistenceConfig("cassandra-journal",
                                                                  "cassandra-snapshot-store",
                                                                  "cassandra-query-journal")
    }
  }
}
