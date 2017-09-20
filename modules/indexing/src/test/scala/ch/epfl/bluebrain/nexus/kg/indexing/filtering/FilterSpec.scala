package ch.epfl.bluebrain.nexus.kg.indexing.filtering

import java.util.regex.Pattern

import akka.http.scaladsl.model.Uri
import ch.epfl.bluebrain.nexus.kg.core.Resources
import ch.epfl.bluebrain.nexus.kg.indexing.filtering.Expr.{ComparisonExpr, InExpr, LogicalExpr}
import ch.epfl.bluebrain.nexus.kg.indexing.filtering.Op._
import ch.epfl.bluebrain.nexus.kg.indexing.filtering.Term.{LiteralTerm, TermCollection, UriTerm}
import org.scalatest.{EitherValues, Matchers, WordSpecLike}

class FilterSpec extends WordSpecLike with Matchers with Resources with EitherValues {

  private val base = "http://localhost/v0"
  private val replacements = Map(Pattern.quote("{{base}}") -> base)
  private implicit val filteringSettings@FilteringSettings(nexusBaseVoc, nexusSearchVoc) =
    FilteringSettings(s"$base/voc/nexus/core", s"$base/voc/nexus/search")

  private val (nxv, nxs) = (Uri(s"$nexusBaseVoc/"), Uri(s"$nexusSearchVoc/"))
  private val prov = Uri("http://www.w3.org/ns/prov#")
  private val rdf = Uri("http://www.w3.org/1999/02/22-rdf-syntax-ns#")
  private val bbpprod = Uri(s"$base/voc/bbp/productionentity/core/")
  private val bbpagent = Uri(s"$base/voc/bbp/agent/core/")

  "A Filter" should {

    "be parsed correctly from json" when {

      "using a single comparison" in {
        val json = jsonContentOf("/filtering/single-comparison.json", replacements)
        val expected = Filter(ComparisonExpr(Eq, UriTerm(s"${nxv}deprecated"), LiteralTerm("false")))
        json.as[Filter] shouldEqual Right(expected)
      }

      "using nested comparisons" in {
        val json = jsonContentOf("/filtering/nested-comparison.json", replacements)
        val expected = Filter(
          LogicalExpr(And, List(
            ComparisonExpr(Eq, UriTerm(s"${prov}wasDerivedFrom"), UriTerm(s"$base/bbp/experiment/subject/v0.1.0/073b4529-83a8-4776-a5a7-676624bfad90")),
            ComparisonExpr(Ne, UriTerm(s"${nxv}deprecated"), LiteralTerm("false")),
            InExpr(UriTerm(s"${rdf}type"), TermCollection(List(UriTerm(s"${prov}Entity"), UriTerm(s"${bbpprod}Circuit")))),
            ComparisonExpr(Lte, UriTerm(s"${nxv}rev"), LiteralTerm("5")),
            LogicalExpr(Xor, List(
              ComparisonExpr(Eq, UriTerm(s"${prov}wasAttributedTo"), UriTerm(s"${bbpagent}sy")),
              ComparisonExpr(Eq, UriTerm(s"${prov}wasAttributedTo"), UriTerm(s"${bbpagent}dmontero")),
            ))
          )))
        json.as[Filter] shouldEqual Right(expected)
      }

      "defining a context that's conflicting with the expected one" in {
        val json = jsonContentOf("/filtering/conflicting-context.json", replacements)
        val expected = Filter(ComparisonExpr(Eq, UriTerm(s"${nxv}deprecated"), LiteralTerm("false")))
        json.as[Filter] shouldEqual Right(expected)
      }
    }

    "fail to parse from a json" when {

      "using a nested or" in {
        val json = jsonContentOf("/filtering/nested-or.json", replacements)
        val expectedHistory = "DownField(filter)/DownField(value)/DownN(4)"
        json.as[Filter].left.value.history.reverse.mkString("/") shouldEqual expectedHistory
      }

      "using a nested xor" in {
        val json = jsonContentOf("/filtering/nested-xor.json", replacements)
        val expectedHistory = "DownField(filter)/DownField(value)/DownN(4)"
        json.as[Filter].left.value.history.reverse.mkString("/") shouldEqual expectedHistory
      }

      "using a nested not" in {
        val json = jsonContentOf("/filtering/nested-not.json", replacements)
        val expectedHistory = "DownField(filter)/DownField(value)/DownN(4)"
        json.as[Filter].left.value.history.reverse.mkString("/") shouldEqual expectedHistory
      }

      "using a multi value for comparison ops" in {
        val json = jsonContentOf("/filtering/single-term-value.json", replacements)
        val expectedHistory = "DownField(filter)/DownField(value)"
        json.as[Filter].left.value.history.reverse.mkString("/") shouldEqual expectedHistory
      }

      "using an incorrect comparison op" in {
        val json = jsonContentOf("/filtering/incorrect-comparison-op.json", replacements)
        val expectedHistory = "DownField(filter)/DownField(value)/DownN(4)/DownField(value)/DownN(0)/DownField(op)"
        json.as[Filter].left.value.history.reverse.mkString("/") shouldEqual expectedHistory
      }

      "using a missing comparison op expression" in {
        val json = jsonContentOf("/filtering/missing-comparison-op.json", replacements)
        val expectedHistory = "DownField(filter)/DownField(value)/DownN(4)/DownField(value)/DownN(0)/DownField(op)"
        json.as[Filter].left.value.history.reverse.mkString("/") shouldEqual expectedHistory
      }

      "using an incorrect logical op" in {
        val json = jsonContentOf("/filtering/incorrect-logical-op.json", replacements)
        val expectedHistory = "DownField(filter)/DownField(value)/DownN(4)/DownField(op)"
        json.as[Filter].left.value.history.reverse.mkString("/") shouldEqual expectedHistory
      }

      "using a missing logical op expression" in {
        val json = jsonContentOf("/filtering/missing-logical-op.json", replacements)
        val expectedHistory = "DownField(filter)/DownField(value)/DownN(4)/DownField(op)"
        json.as[Filter].left.value.history.reverse.mkString("/") shouldEqual expectedHistory
      }

      "using an uncontextualized value" in {
        val json = jsonContentOf("/filtering/uncontextualized-value.json", replacements)
        val expectedHistory = "DownField(filter)/DownField(value)/DownN(4)/DownField(value)/DownN(1)/DownField(value)"
        json.as[Filter].left.value.history.reverse.mkString("/") shouldEqual expectedHistory
      }

      "using a logical op and an empty value" in {
        val json = jsonContentOf("/filtering/logical-op-empty-value.json", replacements)
        val expectedHistory = "DownField(filter)/DownField(value)"
        json.as[Filter].left.value.history.reverse.mkString("/") shouldEqual expectedHistory
      }

      "using a logical non-nested op and an empty value" in {
        val json = jsonContentOf("/filtering/logical-non-nested-op-empty-value.json", replacements)
        val expectedHistory = "DownField(filter)/DownField(value)"
        json.as[Filter].left.value.history.reverse.mkString("/") shouldEqual expectedHistory
      }

      "using a logical op with a non blank node value" in {
        val json = jsonContentOf("/filtering/logical-op-string-value.json", replacements)
        val expectedHistory = "DownField(filter)/DownField(value)"
        json.as[Filter].left.value.history.reverse.mkString("/") shouldEqual expectedHistory
      }

      "using a non uri path" in {
        val json = jsonContentOf("/filtering/non-uri-path.json", replacements)
        val expectedHistory = "DownField(filter)/DownField(path)"
        json.as[Filter].left.value.history.reverse.mkString("/") shouldEqual expectedHistory
      }

      "using a multiple paths" in {
        val json = jsonContentOf("/filtering/multiple-paths.json", replacements)
        val expectedHistory = "DownField(filter)/DownField(path)"
        json.as[Filter].left.value.history.reverse.mkString("/") shouldEqual expectedHistory
      }

      "using a blank node as a term value" in {
        val json = jsonContentOf("/filtering/term-value-blank-node.json", replacements)
        val expectedHistory = "DownField(filter)/DownField(value)/DownN(2)/DownField(value)"
        json.as[Filter].left.value.history.reverse.mkString("/") shouldEqual expectedHistory
      }

      "using a blank node as a term value on non-nested" in {
        val json = jsonContentOf("/filtering/term-value-non-blank-non-nested.json", replacements)
        val expectedHistory = "DownField(filter)/DownField(value)/DownN(4)/DownField(value)"
        json.as[Filter].left.value.history.reverse.mkString("/") shouldEqual expectedHistory
      }

      "using a missing term value" in {
        val json = jsonContentOf("/filtering/term-value-missing.json", replacements)
        val expectedHistory = "DownField(filter)/DownField(value)/DownN(2)/DownField(value)"
        json.as[Filter].left.value.history.reverse.mkString("/") shouldEqual expectedHistory
      }

      "using a missing filter" in {
        val json = jsonContentOf("/filtering/missing-filter.json", replacements)
        val expectedHistory = "DownField(filter)"
        json.as[Filter].left.value.history.reverse.mkString("/") shouldEqual expectedHistory
      }

      "using multiple filters" in {
        val json = jsonContentOf("/filtering/multiple-filters.json", replacements)
        val expectedHistory = "DownField(filter)"
        json.as[Filter].left.value.history.reverse.mkString("/") shouldEqual expectedHistory
      }
    }
  }

}