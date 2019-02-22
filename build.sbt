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
val monixVersion         = "3.0.0-RC2"
val pureconfigVersion    = "0.10.2"

val rdfVersion         = "0.3.0"
val sourcingVersion    = "0.13.0"
val topQuadrantVersion = "1.2.0-nexus4"

lazy val rdf              = "ch.epfl.bluebrain.nexus"              %% "rdf"           % rdfVersion
lazy val sourcingCore     = "ch.epfl.bluebrain.nexus"              %% "sourcing-core" % sourcingVersion
lazy val topQuadrantShacl = "ch.epfl.bluebrain.nexus.org.topbraid" % "shacl"          % topQuadrantVersion

lazy val catsCore           = "org.typelevel"              %% "cats-core"            % catsVersion
lazy val catsEffect         = "org.typelevel"              %% "cats-effect"          % catsEffectVersion
lazy val circeCore          = "io.circe"                   %% "circe-core"           % circeVersion
lazy val circeParser        = "io.circe"                   %% "circe-parser"         % circeVersion
lazy val circeGenericExtras = "io.circe"                   %% "circe-generic-extras" % circeVersion
lazy val scalaTest          = "org.scalatest"              %% "scalatest"            % scalaTestVersion
lazy val shapeless          = "com.chuusai"                %% "shapeless"            % shapelessVersion
lazy val journal            = "io.verizon.journal"         %% "core"                 % journalVersion
lazy val jenaArq            = "org.apache.jena"            % "jena-arq"              % jenaVersion
lazy val blazegraph         = "com.blazegraph"             % "blazegraph-jar"        % blazegraphVersion
lazy val jacksonAnnotations = "com.fasterxml.jackson.core" % "jackson-annotations"   % jacksonVersion
lazy val jacksonCore        = "com.fasterxml.jackson.core" % "jackson-core"          % jacksonVersion
lazy val jacksonDatabind    = "com.fasterxml.jackson.core" % "jackson-databind"      % jacksonVersion

lazy val akkaActor           = "com.typesafe.akka" %% "akka-actor"            % akkaVersion
lazy val akkaCluster         = "com.typesafe.akka" %% "akka-cluster"          % akkaVersion
lazy val akkaClusterSharding = "com.typesafe.akka" %% "akka-cluster-sharding" % akkaVersion
lazy val akkaDistributedData = "com.typesafe.akka" %% "akka-distributed-data" % akkaVersion
lazy val akkaTestKit         = "com.typesafe.akka" %% "akka-testkit"          % akkaVersion
lazy val akkaSlf4j           = "com.typesafe.akka" %% "akka-slf4j"            % akkaVersion
lazy val akkaStream          = "com.typesafe.akka" %% "akka-stream"           % akkaVersion

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
lazy val monixEval         = "io.monix"                          %% "monix-eval"               % monixVersion
lazy val monixTail         = "io.monix"                          %% "monix-tail"               % monixVersion
lazy val pureconfig        = "com.github.pureconfig"             %% "pureconfig"               % pureconfigVersion

lazy val kamonCore       = "io.kamon" %% "kamon-core"            % "1.1.5"
lazy val kamonPrometheus = "io.kamon" %% "kamon-prometheus"      % "1.1.1"
lazy val kamonJaeger     = "io.kamon" %% "kamon-jaeger"          % "1.0.2"
lazy val kamonLogback    = "io.kamon" %% "kamon-logback"         % "1.0.5"
lazy val kamonMetrics    = "io.kamon" %% "kamon-system-metrics"  % "1.0.1"
lazy val kamonAkka       = "io.kamon" %% "kamon-akka-2.5"        % "1.1.3"
lazy val kamonAkkaHttp   = "io.kamon" %% "kamon-akka-http-2.5"   % "1.1.1"
lazy val kamonAkkaRemote = "io.kamon" %% "kamon-akka-remote-2.5" % "1.1.0"

lazy val test = project
  .in(file("modules/test"))
  .settings(
    name                := "commons-test",
    moduleName          := "commons-test",
    coverageEnabled     := false,
    libraryDependencies ++= Seq(akkaTestKit, akkaClusterSharding, catsEffect, circeCore, circeParser, scalaTest)
  )

lazy val core = project
  .in(file("modules/core"))
  .dependsOn(test)
  .settings(
    name       := "commons-core",
    moduleName := "commons-core",
    libraryDependencies ++= Seq(
      akkaActor,
      akkaCluster,
      akkaDistributedData,
      akkaHttp,
      akkaHttpCirce,
      catsCore,
      catsEffect,
      circeCore,
      circeGenericExtras,
      journal,
      kamonCore,
      kamonPrometheus,
      kamonJaeger,
      kamonLogback,
      kamonMetrics,
      kamonAkka % Runtime,
      kamonAkkaHttp,
      kamonAkkaRemote % Runtime,
      pureconfig,
      rdf,
      shapeless,
      sourcingCore,
      topQuadrantShacl,
      akkaHttpTestKit % Test,
      akkaTestKit     % Test,
      akkaSlf4j       % Test,
      scalaTest       % Test
    )
  )

lazy val elasticSearchClient = project
  .in(file("modules/elastic-client"))
  .dependsOn(core, test % Test)
  .enablePlugins(JmhPlugin)
  .settings(
    name       := "elasticsearch-client",
    moduleName := "elasticsearch-client",
    libraryDependencies ++= Seq(
      akkaStream,
      circeCore,
      akkaSlf4j          % Test,
      circeParser        % Test,
      circeGenericExtras % Test,
      esCore             % Test,
      esPainless         % Test,
      esReindex          % Test,
      esRestClient       % Test,
      esTransportClient  % Test,
      log4jCore          % Test,
      log4jApi           % Test
    ),
    sourceDirectory in Jmh     := (sourceDirectory in Test).value,
    classDirectory in Jmh      := (classDirectory in Test).value,
    dependencyClasspath in Jmh := (dependencyClasspath in Test).value,
    compile in Jmh             := (compile in Jmh).dependsOn(compile in Test).value,
    run in Jmh                 := (run in Jmh).dependsOn(Keys.compile in Jmh).evaluated
  )

lazy val sparqlClient = project
  .in(file("modules/sparql-client"))
  .dependsOn(core, test % Test)
  .enablePlugins(JmhPlugin)
  .settings(
    name       := "sparql-client",
    moduleName := "sparql-client",
    libraryDependencies ++= Seq(
      akkaStream,
      circeCore,
      jenaArq,
      rdf,
      akkaSlf4j          % Test,
      akkaTestKit        % Test,
      circeParser        % Test,
      blazegraph         % Test,
      jacksonAnnotations % Test,
      jacksonCore        % Test,
      jacksonDatabind    % Test,
      scalaTest          % Test
    ),
    sourceDirectory in Jmh     := (sourceDirectory in Test).value,
    classDirectory in Jmh      := (classDirectory in Test).value,
    dependencyClasspath in Jmh := (dependencyClasspath in Test).value,
    compile in Jmh             := (compile in Jmh).dependsOn(compile in Test).value,
    run in Jmh                 := (run in Jmh).dependsOn(Keys.compile in Jmh).evaluated
  )

lazy val root = project
  .in(file("."))
  .settings(name := "commons", moduleName := "commons")
  .settings(noPublish)
  .aggregate(core, test, elasticSearchClient, sparqlClient)

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
