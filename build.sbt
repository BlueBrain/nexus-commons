val wesoValidatorVersion = "0.0.65-nexus1"
val jenaVersion          = "3.7.0"
val blazegraphVersion    = "2.1.4"
val jacksonVersion       = "2.9.6"
val catsVersion          = "1.1.0"
val circeVersion         = "0.9.3"
val scalaTestVersion     = "3.0.5"
val shapelessVersion     = "2.3.3"
val journalVersion       = "3.0.19"
val akkaVersion          = "2.5.13"
val akkaHttpVersion      = "10.0.13"
val akkaHttpCirceVersion = "1.21.0"
val elasticSearchVersion = "6.3.0"
val log4jVersion         = "2.11.0"
val commonsIOVersion     = "1.3.2"
val rdfVersion           = "0.2.0"
val monixVersion         = "3.0.0-RC1"

lazy val catsCore           = "org.typelevel"                   %% "cats-core"            % catsVersion
lazy val circeCore          = "io.circe"                        %% "circe-core"           % circeVersion
lazy val circeParser        = "io.circe"                        %% "circe-parser"         % circeVersion
lazy val circeGenericExtras = "io.circe"                        %% "circe-generic-extras" % circeVersion
lazy val circeJava8         = "io.circe"                        %% "circe-java8"          % circeVersion
lazy val scalaTest          = "org.scalatest"                   %% "scalatest"            % scalaTestVersion
lazy val shapeless          = "com.chuusai"                     %% "shapeless"            % shapelessVersion
lazy val journal            = "io.verizon.journal"              %% "core"                 % journalVersion
lazy val wesoSchema         = "com.github.bogdanromanx.es.weso" %% "schema"               % wesoValidatorVersion
lazy val jenaArq            = "org.apache.jena"                 % "jena-arq"              % jenaVersion
lazy val blazegraph         = "com.blazegraph"                  % "blazegraph-jar"        % blazegraphVersion
lazy val jacksonAnnotations = "com.fasterxml.jackson.core"      % "jackson-annotations"   % jacksonVersion
lazy val jacksonCore        = "com.fasterxml.jackson.core"      % "jackson-core"          % jacksonVersion
lazy val jacksonDatabind    = "com.fasterxml.jackson.core"      % "jackson-databind"      % jacksonVersion

lazy val akkaActor   = "com.typesafe.akka" %% "akka-actor"   % akkaVersion
lazy val akkaTestKit = "com.typesafe.akka" %% "akka-testkit" % akkaVersion
lazy val akkaSlf4j   = "com.typesafe.akka" %% "akka-slf4j"   % akkaVersion
lazy val akkaStream  = "com.typesafe.akka" %% "akka-stream"  % akkaVersion

lazy val akkaHttp        = "com.typesafe.akka" %% "akka-http"         % akkaHttpVersion
lazy val akkaHttpTestKit = "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion
lazy val akkaHttpCirce   = "de.heikoseeberger" %% "akka-http-circe"   % akkaHttpCirceVersion
lazy val akkaHttpCore    = "com.typesafe.akka" %% "akka-http-core"    % akkaHttpVersion

lazy val log4jCore         = "org.apache.logging.log4j"          % "log4j-core"                % log4jVersion
lazy val log4jApi          = "org.apache.logging.log4j"          % "log4j-api"                 % log4jVersion
lazy val esCore            = "org.elasticsearch"                 % "elasticsearch"             % elasticSearchVersion
lazy val esPainless        = "org.codelibs.elasticsearch.module" % "lang-painless"             % elasticSearchVersion
lazy val esReindex         = "org.codelibs.elasticsearch.module" % "reindex"                   % elasticSearchVersion
lazy val esRestClient      = "org.elasticsearch.client"          % "elasticsearch-rest-client" % elasticSearchVersion
lazy val esTransportClient = "org.elasticsearch.plugin"          % "transport-netty4-client"   % elasticSearchVersion
lazy val commonsIO         = "org.apache.commons"                % "commons-io"                % commonsIOVersion
lazy val monixTail         = "io.monix"                          %% "monix-tail"               % monixVersion
lazy val rdfJena           = "ch.epfl.bluebrain.nexus"           %% "rdf-jena"                 % rdfVersion

lazy val types = project
  .in(file("modules/types"))
  .settings(
    name                := "commons-types",
    moduleName          := "commons-types",
    libraryDependencies ++= Seq(catsCore, circeCore, circeGenericExtras, circeParser % Test, scalaTest % Test)
  )

lazy val test = project
  .in(file("modules/test"))
  .dependsOn(types)
  .settings(
    name                := "commons-test",
    moduleName          := "commons-test",
    coverageEnabled     := false,
    libraryDependencies ++= Seq(circeCore, circeParser)
  )

lazy val http = project
  .in(file("modules/http"))
  .dependsOn(types, test % Test)
  .settings(
    name       := "commons-http",
    moduleName := "commons-http",
    libraryDependencies ++= Seq(shapeless,
                                akkaHttp,
                                akkaHttpCirce,
                                catsCore,
                                circeCore,
                                journal,
                                monixTail,
                                akkaHttpTestKit    % Test,
                                circeGenericExtras % Test,
                                scalaTest          % Test)
  )

lazy val queryTypes = project
  .in(file("modules/query-types"))
  .settings(
    name                := "commons-query-types",
    moduleName          := "commons-query-types",
    libraryDependencies ++= Seq(catsCore, circeCore, scalaTest % Test, circeGenericExtras % Test)
  )

lazy val elasticServerEmbed = project
  .in(file("modules/elastic-server-embed"))
  .dependsOn(test)
  .settings(
    name       := "elastic-server-embed",
    moduleName := "elastic-server-embed",
    libraryDependencies ++= Seq(
      akkaHttp,
      akkaStream,
      akkaTestKit,
      commonsIO,
      esCore,
      esPainless,
      esReindex,
      esRestClient,
      esTransportClient,
      log4jCore,
      log4jApi,
      scalaTest,
      akkaSlf4j % Test,
    )
  )

lazy val elasticClient = project
  .in(file("modules/elastic-client"))
  .dependsOn(http, queryTypes, test % Test, elasticServerEmbed % Test)
  .settings(
    name       := "elastic-client",
    moduleName := "elastic-client",
    libraryDependencies ++= Seq(
      akkaStream,
      circeCore,
      akkaSlf4j          % Test,
      circeParser        % Test,
      circeGenericExtras % Test
    )
  )

lazy val sparqlClient = project
  .in(file("modules/sparql-client"))
  .dependsOn(http, queryTypes, test % Test)
  .settings(
    name       := "sparql-client",
    moduleName := "sparql-client",
    libraryDependencies ++= Seq(
      akkaStream,
      circeCore,
      jenaArq,
      rdfJena,
      akkaSlf4j          % Test,
      circeParser        % Test,
      blazegraph         % Test,
      jacksonAnnotations % Test,
      jacksonCore        % Test,
      jacksonDatabind    % Test,
      akkaTestKit        % Test,
      scalaTest          % Test
    )
  )

lazy val shaclValidator = project
  .in(file("modules/ld/shacl-validator"))
  .dependsOn(types)
  .settings(
    name       := "shacl-validator",
    moduleName := "shacl-validator",
    resolvers  += Resolver.bintrayRepo("bogdanromanx", "maven"),
    libraryDependencies ++= Seq(journal,
                                wesoSchema,
                                catsCore,
                                circeCore,
                                akkaSlf4j   % Test,
                                circeParser % Test,
                                scalaTest   % Test)
  )

lazy val schemas = project
  .in(file("modules/schemas"))
  .settings(
    name       := "commons-schemas",
    moduleName := "commons-schemas"
  )

lazy val root = project
  .in(file("."))
  .settings(name := "commons", moduleName := "commons")
  .settings(noPublish)
  .aggregate(types, http, test, queryTypes, elasticServerEmbed, elasticClient, sparqlClient, shaclValidator, schemas)

lazy val noPublish = Seq(publishLocal := {}, publish := {}, publishArtifact := false)

inThisBuild(
  Seq(
    homepage := Some(url("https://github.com/BlueBrain/nexus-commons")),
    licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
    scmInfo := Some(
      ScmInfo(url("https://github.com/BlueBrain/nexus-commons"), "scm:git:git@github.com:BlueBrain/nexus-commons.git")),
    developers := List(
      Developer("bogdanromanx", "Bogdan Roman", "noreply@epfl.ch", url("https://bluebrain.epfl.ch/")),
      Developer("hygt", "Henry Genet", "noreply@epfl.ch", url("https://bluebrain.epfl.ch/")),
      Developer("umbreak", "Didac Montero Mendez", "noreply@epfl.ch", url("https://bluebrain.epfl.ch/")),
      Developer("wwajerowicz", "Wojtek Wajerowicz", "noreply@epfl.ch", url("https://bluebrain.epfl.ch/"))
    ),
    // These are the sbt-release-early settings to configure
    releaseEarlyWith              := BintrayPublisher,
    releaseEarlyNoGpg             := true,
    releaseEarlyEnableSyncToMaven := false
  ))

addCommandAlias("review", ";clean;scalafmtSbtCheck;coverage;scapegoat;test;coverageReport;coverageAggregate")
