val wesoValidatorVersion = "0.0.63-nexus2"
val metricsCoreVersion   = "3.2.2"
val jenaVersion          = "3.4.0"
val blazegraphVersion    = "2.1.4"
val jacksonVersion       = "2.9.0"
val monixVersion         = "2.3.0"

lazy val types = project
  .in(file("modules/ch.epfl.bluebrain.nexus.commons.iam.types"))
  .settings(
    name := "commons-ch.epfl.bluebrain.nexus.commons.iam.types",
    moduleName := "commons-ch.epfl.bluebrain.nexus.commons.iam.types",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core"  % catsVersion.value,
      "io.circe"      %% "circe-core" % circeVersion.value,
      "org.scalatest" %% "scalatest"  % scalaTestVersion.value % Test
    )
  )

lazy val iamTypes = project
  .in(file("modules/iam-types"))
  .settings(
    name := "iam-types",
    moduleName := "iam-types",
    libraryDependencies ++= Seq(
      "com.typesafe.akka"  %% "akka-http" % akkaHttpVersion.value,
      "de.heikoseeberger"  %% "akka-http-circe" % akkaHttpCirceVersion.value,
      "io.circe"           %% "circe-core" % circeVersion.value,
      "io.circe"           %% "circe-generic-extras" % circeVersion.value,
      "io.circe"           %% "circe-optics" % circeVersion.value,
      "io.circe"           %% "circe-parser" % circeVersion.value,
      "io.verizon.journal" %% "core" % journalVersion.value,
      "org.scalatest"      %% "scalatest" % scalaTestVersion.value % Test
    )
  )

lazy val sourcing = project
  .in(file("modules/sourcing/core"))
  .dependsOn(types)
  .settings(
    name := "sourcing-core",
    moduleName := "sourcing-core",
    libraryDependencies ++= Seq("org.typelevel" %% "cats-core" % catsVersion.value,
                                "org.scalatest" %% "scalatest" % scalaTestVersion.value % Test)
  )

lazy val sourcingAkka = project
  .in(file("modules/sourcing/akka"))
  .dependsOn(sourcing % "compile->compile;test->test")
  .settings(
    name := "sourcing-akka",
    moduleName := "sourcing-akka",
    libraryDependencies ++= Seq(
      "com.chuusai"         %% "shapeless"                 % shapelessVersion.value,
      "com.typesafe.akka"   %% "akka-persistence"          % akkaVersion.value,
      "com.typesafe.akka"   %% "akka-persistence-query"    % akkaVersion.value,
      "com.typesafe.akka"   %% "akka-cluster-sharding"     % akkaVersion.value,
      "com.typesafe.akka"   %% "akka-testkit"              % akkaVersion.value % Test,
      "com.github.dnvriend" %% "akka-persistence-inmemory" % akkaPersistenceInMemVersion.value % Test,
      "org.scalatest"       %% "scalatest"                 % scalaTestVersion.value % Test
    )
  )

lazy val sourcingMem = project
  .in(file("modules/sourcing/mem"))
  .dependsOn(sourcing % "compile->compile;test->test")
  .settings(name := "sourcing-mem",
            moduleName := "sourcing-mem",
            libraryDependencies ++= Seq("org.scalatest" %% "scalatest" % scalaTestVersion.value % Test))

lazy val service = project
  .in(file("modules/service"))
  .dependsOn(types, sourcingAkka % "test->compile")
  .settings(
    name := "commons-service",
    moduleName := "commons-service",
    libraryDependencies ++= Seq(
      "com.chuusai"           %% "shapeless"                           % shapelessVersion.value,
      "com.typesafe.akka"     %% "akka-actor"                          % akkaVersion.value,
      "com.typesafe.akka"     %% "akka-distributed-data"               % akkaVersion.value,
      "com.typesafe.akka"     %% "akka-http"                           % akkaHttpVersion.value,
      "com.typesafe.akka"     %% "akka-persistence-cassandra"          % akkaPersistenceCassandraVersion.value,
      "io.circe"              %% "circe-core"                          % circeVersion.value,
      "io.circe"              %% "circe-parser"                        % circeVersion.value,
      "io.monix"              %% "monix"                               % monixVersion,
      "io.verizon.journal"    %% "core"                                % journalVersion.value,
      "io.dropwizard.metrics" % "metrics-core"                         % metricsCoreVersion % Test,
      "com.typesafe.akka"     %% "akka-persistence-cassandra-launcher" % akkaPersistenceCassandraVersion.value % Test,
      "com.typesafe.akka"     %% "akka-testkit"                        % akkaVersion.value % Test,
      "com.typesafe.akka"     %% "akka-http-testkit"                   % akkaHttpVersion.value % Test,
      "io.circe"              %% "circe-generic-extras"                % circeVersion.value % Test,
      "org.scalatest"         %% "scalatest"                           % scalaTestVersion.value % Test
    )
  )

lazy val test = project
  .in(file("modules/test"))
  .dependsOn(types)
  .settings(
    name := "commons-test",
    moduleName := "commons-test",
    coverageEnabled := false,
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core"   % circeVersion.value,
      "io.circe" %% "circe-parser" % circeVersion.value,
    )
  )

lazy val http = project
  .in(file("modules/http"))
  .dependsOn(types)
  .settings(
    name := "commons-http",
    moduleName := "commons-http",
    libraryDependencies ++= Seq(
      "com.chuusai"        %% "shapeless" % shapelessVersion.value,
      "com.typesafe.akka"  %% "akka-http" % akkaHttpVersion.value,
      "io.verizon.journal" %% "core"      % journalVersion.value,
      "org.scalatest"      %% "scalatest" % scalaTestVersion.value % Test
    )
  )

lazy val shaclValidator = project
  .in(file("modules/ld/shacl-validator"))
  .dependsOn(types)
  .settings(
    name := "shacl-validator",
    moduleName := "shacl-validator",
    resolvers += Resolver.bintrayRepo("bogdanromanx", "maven"),
    libraryDependencies ++= Seq(
      "io.verizon.journal"              %% "core"         % journalVersion.value,
      "com.github.bogdanromanx.es.weso" %% "schema"       % wesoValidatorVersion,
      "org.typelevel"                   %% "cats-core"    % catsVersion.value,
      "io.circe"                        %% "circe-core"   % circeVersion.value,
      "io.circe"                        %% "circe-parser" % circeVersion.value % Test,
      "org.scalatest"                   %% "scalatest"    % scalaTestVersion.value % Test
    )
  )

lazy val sparqlClient = project
  .in(file("modules/ld/sparql-client"))
  .dependsOn(http)
  .settings(
    name := "sparql-client",
    moduleName := "sparql-client",
    libraryDependencies ++= Seq(
      "com.typesafe.akka"          %% "akka-actor"         % akkaVersion.value,
      "com.typesafe.akka"          %% "akka-stream"        % akkaVersion.value,
      "org.apache.jena"            % "jena-arq"            % jenaVersion,
      "de.heikoseeberger"          %% "akka-http-circe"    % akkaHttpCirceVersion.value,
      "io.circe"                   %% "circe-core"         % circeVersion.value,
      "io.circe"                   %% "circe-parser"       % circeVersion.value % Test,
      "com.blazegraph"             % "blazegraph-jar"      % blazegraphVersion % Test,
      "com.fasterxml.jackson.core" % "jackson-annotations" % jacksonVersion % Test,
      "com.fasterxml.jackson.core" % "jackson-core"        % jacksonVersion % Test,
      "com.fasterxml.jackson.core" % "jackson-databind"    % jacksonVersion % Test,
      "com.typesafe.akka"          %% "akka-testkit"       % akkaVersion.value % Test,
      "org.scalatest"              %% "scalatest"          % scalaTestVersion.value % Test
    )
  )

lazy val root = project
  .in(file("."))
  .settings(name := "commons", moduleName := "commons")
  .settings(noPublish)
  .aggregate(types, sourcing, sourcingAkka, sourcingMem, http, test, service, shaclValidator, sparqlClient, iamTypes

  )

lazy val noPublish = Seq(publishLocal := {}, publish := {})

addCommandAlias("review", ";clean;coverage;scapegoat;test;coverageReport;coverageAggregate")
addCommandAlias("rel", ";release with-defaults")
