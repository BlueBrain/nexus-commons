val wesoValidatorVersion = "0.0.65-nexus1"
val jenaVersion          = "3.10.0"
val blazegraphVersion    = "2.1.4"
val jacksonVersion       = "2.9.8"
val catsVersion          = "1.6.0"
val catsEffectVersion    = "1.2.0"
val circeVersion         = "0.11.1"
val scalaTestVersion     = "3.0.5"
val shapelessVersion     = "2.3.3"
val journalVersion       = "3.0.19"
val akkaVersion          = "2.5.21"
val akkaHttpVersion      = "10.1.7"
val akkaHttpCirceVersion = "1.25.2"
val elasticSearchVersion = "6.6.0"
val log4jVersion         = "2.11.2"
val commonsIOVersion     = "1.3.2"
val rdfVersion           = "0.2.33"
val monixVersion         = "3.0.0-RC2"
val topQuadrantVersion   = "1.2.0-nexus3"

lazy val catsCore           = "org.typelevel"                   %% "cats-core"            % catsVersion
lazy val catsEffect         = "org.typelevel"                   %% "cats-effect"          % catsEffectVersion
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

lazy val log4jCore         = "org.apache.logging.log4j"             % "log4j-core"                % log4jVersion
lazy val log4jApi          = "org.apache.logging.log4j"             % "log4j-api"                 % log4jVersion
lazy val esCore            = "org.elasticsearch"                    % "elasticsearch"             % elasticSearchVersion
lazy val esPainless        = "org.codelibs.elasticsearch.module"    % "lang-painless"             % elasticSearchVersion
lazy val esReindex         = "org.codelibs.elasticsearch.module"    % "reindex"                   % elasticSearchVersion
lazy val esRestClient      = "org.elasticsearch.client"             % "elasticsearch-rest-client" % elasticSearchVersion
lazy val esTransportClient = "org.elasticsearch.plugin"             % "transport-netty4-client"   % elasticSearchVersion
lazy val commonsIO         = "org.apache.commons"                   % "commons-io"                % commonsIOVersion
lazy val monixEval         = "io.monix"                             %% "monix-eval"               % monixVersion
lazy val monixTail         = "io.monix"                             %% "monix-tail"               % monixVersion
lazy val rdfCirce          = "ch.epfl.bluebrain.nexus"              %% "rdf-circe"                % rdfVersion
lazy val rdfJena           = "ch.epfl.bluebrain.nexus"              %% "rdf-jena"                 % rdfVersion
lazy val topQuadrantShacl  = "ch.epfl.bluebrain.nexus.org.topbraid" % "shacl"                     % topQuadrantVersion

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
    libraryDependencies ++= Seq(catsEffect, circeCore, circeParser, scalaTest)
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
                                catsEffect,
                                circeCore,
                                journal,
                                akkaHttpTestKit    % Test,
                                akkaTestKit        % Test,
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

lazy val elasticSearchClient = project
  .in(file("modules/elastic-client"))
  .dependsOn(http, queryTypes, test % Test, elasticServerEmbed % Test)
  .enablePlugins(JmhPlugin)
  .settings(
    name       := "elastic-search-client",
    moduleName := "elastic-search-client",
    libraryDependencies ++= Seq(
      akkaStream,
      circeCore,
      akkaSlf4j          % Test,
      circeParser        % Test,
      circeGenericExtras % Test
    ),
    sourceDirectory in Jmh     := (sourceDirectory in Test).value,
    classDirectory in Jmh      := (classDirectory in Test).value,
    dependencyClasspath in Jmh := (dependencyClasspath in Test).value,
    compile in Jmh             := (compile in Jmh).dependsOn(compile in Test).value,
    run in Jmh                 := (run in Jmh).dependsOn(Keys.compile in Jmh).evaluated
  )

lazy val sparqlClient = project
  .in(file("modules/sparql-client"))
  .dependsOn(http, queryTypes, test % Test)
  .enablePlugins(JmhPlugin)
  .settings(
    name       := "sparql-client",
    moduleName := "sparql-client",
    libraryDependencies ++= Seq(
      akkaStream,
      circeCore,
      jenaArq,
      rdfJena,
      akkaSlf4j          % Test,
      akkaTestKit        % Test,
      circeParser        % Test,
      blazegraph         % Test,
      jacksonAnnotations % Test,
      jacksonCore        % Test,
      jacksonDatabind    % Test,
      rdfCirce           % Test,
      scalaTest          % Test
    ),
    sourceDirectory in Jmh     := (sourceDirectory in Test).value,
    classDirectory in Jmh      := (classDirectory in Test).value,
    dependencyClasspath in Jmh := (dependencyClasspath in Test).value,
    compile in Jmh             := (compile in Jmh).dependsOn(compile in Test).value,
    run in Jmh                 := (run in Jmh).dependsOn(Keys.compile in Jmh).evaluated
  )

lazy val shaclValidator = project
  .in(file("modules/ld/shacl-validator"))
  .dependsOn(types)
  .settings(
    name       := "shacl-shaclex-validator",
    moduleName := "shacl-shaclex-validator",
    resolvers  += Resolver.bintrayRepo("bogdanromanx", "maven"),
    libraryDependencies ++= Seq(journal,
                                wesoSchema,
                                catsCore,
                                circeCore,
                                akkaSlf4j   % Test,
                                circeParser % Test,
                                scalaTest   % Test)
  )

lazy val shaclValidatorTQ = project
  .in(file("modules/ld/shacl-topquadrant-validator"))
  .dependsOn(http, test)
  .settings(
    name       := "shacl-topquadrant-validator",
    moduleName := "shacl-topquadrant-validator",
    resolvers  += Resolver.bintrayRepo("bogdanromanx", "maven"),
    libraryDependencies ++= Seq(catsCore,
                                journal,
                                rdfCirce,
                                topQuadrantShacl,
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
  .aggregate(types,
             http,
             test,
             queryTypes,
             elasticServerEmbed,
             elasticSearchClient,
             sparqlClient,
             shaclValidator,
             shaclValidatorTQ,
             schemas)

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
