/*
scalafmt: {
  style = defaultWithAlign
  maxColumn = 150
  align.tokens = [
    { code = "=>", owner = "Case" }
    { code = "?", owner = "Case" }
    { code = "extends", owner = "Defn.(Class|Trait|Object)" }
    { code = "//", owner = ".*" }
    { code = "{", owner = "Template" }
    { code = "}", owner = "Template" }
    { code = ":=", owner = "Term.ApplyInfix" }
    { code = "++=", owner = "Term.ApplyInfix" }
    { code = "+=", owner = "Term.ApplyInfix" }
    { code = "%", owner = "Term.ApplyInfix" }
    { code = "%%", owner = "Term.ApplyInfix" }
    { code = "%%%", owner = "Term.ApplyInfix" }
    { code = "->", owner = "Term.ApplyInfix" }
    { code = "?", owner = "Term.ApplyInfix" }
    { code = "<-", owner = "Enumerator.Generator" }
    { code = "?", owner = "Enumerator.Generator" }
    { code = "=", owner = "(Enumerator.Val|Defn.(Va(l|r)|Def|Type))" }
  ]
}
 */

// Dependency versions
val adminVersion                = "0.2.8"
val iamVersion                  = "0.10.23"
val commonsVersion              = "0.10.18"
val rdfVersion                  = "0.2.17"
val serviceVersion              = "0.10.14"
val sourcingVersion             = "0.10.7"
val akkaVersion                 = "2.5.14"
val akkaCorsVersion             = "0.3.0"
val akkaHttpVersion             = "10.1.3"
val akkaPersistenceInMemVersion = "2.5.1.1"
val akkaPersistenceCassVersion  = "0.88"
val catsVersion                 = "1.2.0"
val catsEffectVersion           = "1.0.0-RC2"
val circeVersion                = "0.9.3"
val journalVersion              = "3.0.19"
val logbackVersion              = "1.2.3"
val mockitoVersion              = "2.21.0"
val monixVersion                = "3.0.0-RC1"
val pureconfigVersion           = "0.9.1"
val shapelessVersion            = "2.3.3"
val scalaTestVersion            = "3.0.5"
val wesoValidatorVersion        = "0.0.65-nexus1"

// Dependencies modules
lazy val adminClient          = "ch.epfl.bluebrain.nexus" %% "admin-client"                % adminVersion
lazy val iamClient            = "ch.epfl.bluebrain.nexus" %% "iam-client"                  % iamVersion
lazy val elasticClient        = "ch.epfl.bluebrain.nexus" %% "elastic-client"              % commonsVersion
lazy val rdfCore              = "ch.epfl.bluebrain.nexus" %% "rdf-core"                    % rdfVersion
lazy val rdfJena              = "ch.epfl.bluebrain.nexus" %% "rdf-jena"                    % rdfVersion
lazy val rdfAkka              = "ch.epfl.bluebrain.nexus" %% "rdf-akka"                    % rdfVersion
lazy val rdfCirce             = "ch.epfl.bluebrain.nexus" %% "rdf-circe"                   % rdfVersion
lazy val rdfNexus             = "ch.epfl.bluebrain.nexus" %% "rdf-nexus"                   % rdfVersion
lazy val serviceIndexing      = "ch.epfl.bluebrain.nexus" %% "service-indexing"            % serviceVersion
lazy val serviceKafka         = "ch.epfl.bluebrain.nexus" %% "service-kafka"               % serviceVersion
lazy val serviceKamon         = "ch.epfl.bluebrain.nexus" %% "service-kamon"               % serviceVersion
lazy val serviceHttp          = "ch.epfl.bluebrain.nexus" %% "service-http"                % serviceVersion
lazy val sourcingCore         = "ch.epfl.bluebrain.nexus" %% "sourcing-core"               % sourcingVersion
lazy val sourcingAkka         = "ch.epfl.bluebrain.nexus" %% "sourcing-akka"               % sourcingVersion
lazy val sourcingMem          = "ch.epfl.bluebrain.nexus" %% "sourcing-mem"                % sourcingVersion
lazy val shaclValidator       = "ch.epfl.bluebrain.nexus" %% "shacl-validator"             % commonsVersion
lazy val sparqlClient         = "ch.epfl.bluebrain.nexus" %% "sparql-client"               % commonsVersion
lazy val akkaCluster          = "com.typesafe.akka"       %% "akka-cluster"                % akkaVersion
lazy val akkaClusterSharding  = "com.typesafe.akka"       %% "akka-cluster-sharding"       % akkaVersion
lazy val akkaDistributedData  = "com.typesafe.akka"       %% "akka-distributed-data"       % akkaVersion
lazy val akkaHttp             = "com.typesafe.akka"       %% "akka-http"                   % akkaHttpVersion
lazy val akkaHttpTestKit      = "com.typesafe.akka"       %% "akka-http-testkit"           % akkaHttpVersion
lazy val akkaPersistence      = "com.typesafe.akka"       %% "akka-persistence"            % akkaVersion
lazy val akkaPersistenceCass  = "com.typesafe.akka"       %% "akka-persistence-cassandra"  % akkaPersistenceCassVersion
lazy val akkaPersistenceInMem = "com.github.dnvriend"     %% "akka-persistence-inmemory"   % akkaPersistenceInMemVersion
lazy val akkaSlf4j            = "com.typesafe.akka"       %% "akka-slf4j"                  % akkaVersion
lazy val akkaStream           = "com.typesafe.akka"       %% "akka-stream"                 % akkaVersion
lazy val catsCore             = "org.typelevel"           %% "cats-core"                   % catsVersion
lazy val catsEffect           = "org.typelevel"           %% "cats-effect"                 % catsEffectVersion
lazy val circeCore            = "io.circe"                %% "circe-core"                  % circeVersion
lazy val journalCore          = "io.verizon.journal"      %% "core"                        % journalVersion
lazy val mockitoCore          = "org.mockito"             % "mockito-core"                 % mockitoVersion
lazy val logbackClassic       = "ch.qos.logback"          % "logback-classic"              % logbackVersion
lazy val monixTail            = "io.monix"                %% "monix-tail"                  % monixVersion
lazy val pureconfig           = "com.github.pureconfig"   %% "pureconfig"                  % pureconfigVersion
lazy val scalaTest            = "org.scalatest"           %% "scalatest"                   % scalaTestVersion
lazy val shapeless            = "com.chuusai"             %% "shapeless"                   % shapelessVersion
lazy val topQuadrantShacl     = "ch.epfl.bluebrain.nexus" %% "shacl-topquadrant-validator" % commonsVersion

lazy val kg = project
  .in(file("."))
  .settings(testSettings, buildInfoSettings)

// Nexus dependencies
lazy val forwardClient     = "hbp.kg.nexus"            %% "forward-client"       % "1.0.0"

lazy val docs = project
  .in(file("docs"))
  .enablePlugins(ParadoxPlugin)
  .settings(common)
  .settings(
    name                         := "kg-docs",
    moduleName                   := "kg-docs",
    paradoxTheme                 := Some(builtinParadoxTheme("generic")),
    paradoxProperties in Compile ++= Map("extref.service.base_url" -> "../"),
    target in (Compile, paradox) := (resourceManaged in Compile).value / "docs",
    resourceGenerators in Compile += {
      (paradox in Compile).map { parent =>
        (parent ** "*").get
      }.taskValue
    }
  )

lazy val schemas = project
  .in(file("modules/kg-schemas"))
  .enablePlugins(WorkbenchPlugin)
  .disablePlugins(ScapegoatSbtPlugin, DocumentationPlugin)
  .settings(common)
  .settings(
    name                := "kg-schemas",
    moduleName          := "kg-schemas",
    libraryDependencies += commonsSchemas
  )

lazy val core = project
  .in(file("modules/core"))
  .dependsOn(schemas)
  .settings(common)
  .settings(
    name       := "kg-core",
    moduleName := "kg-core",
    libraryDependencies ++= Seq(
      akkaClusterSharding,
      commonsIAM,
      commonsQueryTypes,
      circeCore,
      circeOptics,
      circeParser,
      jenaArq,
      jenaQueryBuilder,
      journalCore,
      shaclValidator,
      sourcingCore,
      sourcingMem     % Test,
      commonsTest     % Test,
      scalaTest       % Test,
      akkaHttpTestkit % Test,
      akkaTestkit     % Test
    ),
    Test / fork              := true,
    Test / parallelExecution := false // workaround for jena initialization
  )

lazy val sparql = project
  .in(file("modules/sparql"))
  .dependsOn(core)
  .settings(common)
  .settings(
    name       := "kg-sparql",
    moduleName := "kg-sparql",
    libraryDependencies ++= Seq(
      akkaHttp,
      akkaHttpCirce,
      akkaStream,
      circeCore,
      circeParser,
      journalCore,
      commonsIAM,
      sourcingCore,
      sparqlClient,
      akkaSlf4j          % Test,
      akkaTestkit        % Test,
      blazegraph         % Test,
      commonsTest        % Test,
      jacksonAnnotations % Test,
      jacksonCore        % Test,
      jacksonDatabind    % Test,
      scalaTest          % Test,
      sourcingMem        % Test
    ),
    Test / fork              := true,
    Test / parallelExecution := false // workaround for jena initialization
  )

lazy val elastic = project
  .in(file("modules/elastic"))
  .dependsOn(core)
  .settings(common)
  .settings(
    name       := "kg-elastic",
    moduleName := "kg-elastic",
    libraryDependencies ++= Seq(
      akkaHttp,
      akkaHttpCirce,
      akkaStream,
      circeCore,
      circeParser,
      commonsIAM,
      elasticClient,
      journalCore,
      sourcingCore,
      akkaTestkit  % Test,
      asm          % Test,
      commonsTest  % Test,
      elasticEmbed % Test,
      scalaTest    % Test,
      sourcingMem  % Test
    ),
    Test / fork              := true,
    Test / parallelExecution := false // workaround for jena initialization
  )

lazy val forward = project
  .in(file("modules/forward"))
  .dependsOn(core)
  .settings(common)
  .settings(
    name       := "kg-forward",
    moduleName := "kg-forward",
    libraryDependencies ++= Seq(
      akkaHttp,
      akkaHttpCirce,
      akkaStream,
      circeCore,
      circeParser,
      commonsIAM,
      forwardClient,
      journalCore,
      sourcingCore,
      akkaTestkit  % Test,
      asm          % Test,
      commonsTest  % Test,
      scalaTest    % Test,
      sourcingMem  % Test
    ),
    Test / fork              := true,
    Test / parallelExecution := false // workaround for jena initialization
  )

lazy val service = project
  .in(file("modules/service"))
  .dependsOn(core % "test->test;compile->compile", sparql, elastic, forward, docs)
  .enablePlugins(BuildInfoPlugin, ServicePackagingPlugin)
  .settings(
    name       := "kg",
    moduleName := "kg",
    libraryDependencies ++= Seq(
      adminClient,
      iamClient,
      rdfAkka,
      rdfCore,
      rdfCirce,
      rdfJena,
      rdfNexus,
      serviceIndexing,
      sourcingAkka,
      akkaDistributedData,
      akkaHttp,
      akkaPersistenceCass,
      akkaStream,
      akkaSlf4j,
      akkaCluster,
      catsCore,
      catsEffect,
      circeCore,
      elasticClient,
      journalCore,
      forwardClient,
      logbackClassic,
      monixTail,
      pureconfig,
      sparqlClient,
      serviceKafka,
      serviceKamon,
      serviceHttp,
      topQuadrantShacl,
      akkaHttpTestKit      % Test,
      akkaPersistenceInMem % Test,
      mockitoCore          % Test,
      scalaTest            % Test,
      sourcingMem          % Test
    ),
    cleanFiles ++= (baseDirectory.value * "ddata*").get
  )

lazy val testSettings = Seq(
  Test / testOptions       += Tests.Argument(TestFrameworks.ScalaTest, "-o", "-u", "target/test-reports"),
  Test / parallelExecution := false
)

lazy val buildInfoSettings = Seq(
  buildInfoKeys    := Seq[BuildInfoKey](version),
  buildInfoPackage := "ch.epfl.bluebrain.nexus.kg.config"
)

inThisBuild(
  List(
    homepage := Some(url("https://github.com/BlueBrain/nexus-kg")),
    licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
    scmInfo  := Some(ScmInfo(url("https://github.com/BlueBrain/nexus-kg"), "scm:git:git@github.com:BlueBrain/nexus-kg.git")),
    developers := List(
      Developer("bogdanromanx", "Bogdan Roman", "noreply@epfl.ch", url("https://bluebrain.epfl.ch/")),
      Developer("hygt", "Henry Genet", "noreply@epfl.ch", url("https://bluebrain.epfl.ch/")),
      Developer("umbreak", "Didac Montero Mendez", "noreply@epfl.ch", url("https://bluebrain.epfl.ch/")),
      Developer("wwajerowicz", "Wojtek Wajerowicz", "noreply@epfl.ch", url("https://bluebrain.epfl.ch/")),
      Developer("rdiana", "Remi Diana", "noreply@epfl.ch", url("https://bluebrain.epfl.ch/"))
    ),
    // These are the sbt-release-early settings to configure
    releaseEarlyWith              := BintrayPublisher,
    releaseEarlyNoGpg             := true,
    releaseEarlyEnableSyncToMaven := false
  )
)

addCommandAlias("review", ";clean;scalafmtSbt;scalafmtSbtCheck;coverage;scapegoat;test;coverageReport;coverageAggregate")
