val wesoValidatorVersion            = "0.0.63-nexus2"
val metricsCoreVersion              = "3.2.2"
val jenaVersion                     = "3.3.0"
val blazegraphVersion               = "2.1.4"
val jacksonVersion                  = "2.9.0"
val monixVersion                    = "2.3.0"
val catsVersion                     = "0.9.0"
val circeVersion                    = "0.8.0"
val scalaTestVersion                = "3.0.4"
val shapelessVersion                = "2.3.2"
val journalVersion                  = "3.0.18"
val akkaVersion                     = "2.5.4"
val akkaPersistenceInMemVersion     = "2.5.1.1"
val akkaPersistenceCassandraVersion = "0.55"
val akkaHttpVersion                 = "10.0.10"
val akkaHttpCirceVersion            = "1.18.0"
val elasticSearchVersion            = "6.1.1"
val log4jVersion                    = "2.10.0"
val commonsio                       = "1.3.2"

lazy val catsCore           = "org.typelevel"                   %% "cats-core"                           % catsVersion
lazy val circeCore          = "io.circe"                        %% "circe-core"                          % circeVersion
lazy val circeParser        = "io.circe"                        %% "circe-parser"                        % circeVersion
lazy val circeGenericExtras = "io.circe"                        %% "circe-generic-extras"                % circeVersion
lazy val circeJava8         = "io.circe"                        %% "circe-java8"                         % circeVersion
lazy val scalaTest          = "org.scalatest"                   %% "scalatest"                           % scalaTestVersion
lazy val shapeless          = "com.chuusai"                     %% "shapeless"                           % shapelessVersion
lazy val monixEval          = "io.monix"                        %% "monix-eval"                          % monixVersion
lazy val journal            = "io.verizon.journal"              %% "core"                                % journalVersion
lazy val metricsCore        = "io.dropwizard.metrics"           % "metrics-core"                         % metricsCoreVersion
lazy val cassandraLauncher  = "com.typesafe.akka"               %% "akka-persistence-cassandra-launcher" % akkaPersistenceCassandraVersion
lazy val wesoSchema         = "com.github.bogdanromanx.es.weso" %% "schema"                              % wesoValidatorVersion
lazy val jenaArq            = "org.apache.jena"                 % "jena-arq"                             % jenaVersion
lazy val blazegraph         = "com.blazegraph"                  % "blazegraph-jar"                       % blazegraphVersion
lazy val jacksonAnnotations = "com.fasterxml.jackson.core"      % "jackson-annotations"                  % jacksonVersion
lazy val jacksonCore        = "com.fasterxml.jackson.core"      % "jackson-core"                         % jacksonVersion
lazy val jacksonDatabind    = "com.fasterxml.jackson.core"      % "jackson-databind"                     % jacksonVersion

lazy val akkaActor           = "com.typesafe.akka" %% "akka-actor"            % akkaVersion
lazy val akkaTestKit         = "com.typesafe.akka" %% "akka-testkit"          % akkaVersion
lazy val akkaClusterSharding = "com.typesafe.akka" %% "akka-cluster-sharding" % akkaVersion
lazy val akkaDistributedData = "com.typesafe.akka" %% "akka-distributed-data" % akkaVersion
lazy val akkaStream          = "com.typesafe.akka" %% "akka-stream"           % akkaVersion

lazy val akkaPersistence          = "com.typesafe.akka"   %% "akka-persistence"           % akkaVersion
lazy val akkaPersistenceQuery     = "com.typesafe.akka"   %% "akka-persistence-query"     % akkaVersion
lazy val akkaPersistenceCassandra = "com.typesafe.akka"   %% "akka-persistence-cassandra" % akkaPersistenceCassandraVersion
lazy val akkaPersistenceInMem     = "com.github.dnvriend" %% "akka-persistence-inmemory"  % akkaPersistenceInMemVersion

lazy val akkaHttp        = "com.typesafe.akka" %% "akka-http"         % akkaHttpVersion
lazy val akkaHttpTestKit = "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion
lazy val akkaHttpCirce   = "de.heikoseeberger" %% "akka-http-circe"   % akkaHttpCirceVersion

lazy val types = project
  .in(file("modules/types"))
  .settings(
    name                := "commons-types",
    moduleName          := "commons-types",
    libraryDependencies ++= Seq(catsCore, circeCore, scalaTest % Test)
  )

lazy val sourcing = project
  .in(file("modules/sourcing/core"))
  .dependsOn(types)
  .settings(
    name                := "sourcing-core",
    moduleName          := "sourcing-core",
    libraryDependencies ++= Seq(catsCore, scalaTest % Test)
  )

lazy val sourcingAkka = project
  .in(file("modules/sourcing/akka"))
  .dependsOn(sourcing % "compile->compile;test->test")
  .settings(
    name       := "sourcing-akka",
    moduleName := "sourcing-akka",
    libraryDependencies ++= Seq(
      shapeless,
      akkaPersistence,
      akkaPersistenceQuery,
      akkaClusterSharding,
      akkaTestKit          % Test,
      akkaPersistenceInMem % Test,
      scalaTest            % Test
    )
  )

lazy val sourcingMem = project
  .in(file("modules/sourcing/mem"))
  .dependsOn(sourcing % "compile->compile;test->test")
  .settings(name := "sourcing-mem", moduleName := "sourcing-mem", libraryDependencies ++= Seq(scalaTest % Test))

lazy val service = project
  .in(file("modules/service"))
  .dependsOn(types, http, sourcingAkka % "test->compile")
  .settings(
    name       := "commons-service",
    moduleName := "commons-service",
    libraryDependencies ++= Seq(
      shapeless,
      akkaActor,
      akkaDistributedData,
      akkaHttp,
      akkaPersistenceCassandra,
      circeCore,
      circeParser,
      monixEval,
      journal,
      metricsCore        % Test,
      cassandraLauncher  % Test,
      akkaTestKit        % Test,
      akkaHttpTestKit    % Test,
      circeGenericExtras % Test,
      scalaTest          % Test
    )
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
                                journal,
                                scalaTest          % Test,
                                akkaHttpTestKit    % Test,
                                circeGenericExtras % Test)
  )

lazy val iam = project
  .in(file("modules/iam"))
  .dependsOn(http, test)
  .settings(
    name       := "iam",
    moduleName := "iam",
    libraryDependencies ++= Seq(akkaHttpCirce,
                                circeGenericExtras,
                                circeParser,
                                circeJava8,
                                akkaTestKit % Test,
                                scalaTest   % Test)
  )

lazy val queryTypes = project
  .in(file("modules/query-types"))
  .settings(
    name                := "commons-query-types",
    moduleName          := "commons-query-types",
    libraryDependencies ++= Seq(catsCore, circeCore, shapeless, scalaTest % Test, circeGenericExtras % Test)
  )

lazy val elasticClient = project
  .in(file("modules/elastic-client"))
  .dependsOn(http, queryTypes, test)
  .settings(
    name       := "elastic-client",
    moduleName := "elastic-client",
    libraryDependencies ++= Seq(
      akkaStream,
      circeCore,
      circeParser                % Test,
      circeGenericExtras         % Test,
      akkaTestKit                % Test,
      scalaTest                  % Test,
      "org.apache.logging.log4j" % "log4j-api" % log4jVersion % Test,
      "org.apache.logging.log4j" % "log4j-core" % log4jVersion % Test,
      "org.elasticsearch"        % "elasticsearch" % elasticSearchVersion % Test,
      "org.elasticsearch.client" % "elasticsearch-rest-client" % elasticSearchVersion % Test,
      "org.elasticsearch.plugin" % "transport-netty4-client" % elasticSearchVersion % Test,
      "org.apache.commons"       % "commons-io" % commonsio % Test
    )
  )

lazy val sparqlClient = project
  .in(file("modules/sparql-client"))
  .dependsOn(http, queryTypes)
  .settings(
    name       := "sparql-client",
    moduleName := "sparql-client",
    libraryDependencies ++= Seq(
      akkaStream,
      jenaArq,
      circeCore,
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
    name                := "shacl-validator",
    moduleName          := "shacl-validator",
    resolvers           += Resolver.bintrayRepo("bogdanromanx", "maven"),
    libraryDependencies ++= Seq(journal, wesoSchema, catsCore, circeCore, circeParser % Test, scalaTest % Test)
  )

lazy val schemas = project
  .in(file("modules/schemas"))
  .enablePlugins(WorkbenchPlugin)
  .settings(
    name             := "commons-schemas",
    moduleName       := "commons-schemas",
    workbenchVersion := "0.2.0"
  )

lazy val root = project
  .in(file("."))
  .settings(name := "commons", moduleName := "commons")
  .settings(noPublish)
  .aggregate(types,
             sourcing,
             sourcingAkka,
             sourcingMem,
             http,
             test,
             service,
             queryTypes,
             elasticClient,
             sparqlClient,
             shaclValidator,
             iam,
             schemas)

lazy val noPublish = Seq(publishLocal := {}, publish := {})

addCommandAlias("review", ";clean;coverage;scapegoat;test;coverageReport;coverageAggregate")
addCommandAlias("rel", ";release with-defaults skip-tests")
