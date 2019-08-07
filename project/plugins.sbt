resolvers += Resolver.bintrayRepo("bbp", "nexus-releases")

addSbtPlugin("ch.epfl.bluebrain.nexus" % "sbt-nexus"     % "0.12.0")
addSbtPlugin("pl.project13.scala"      % "sbt-jmh"       % "0.3.7")
addSbtPlugin("com.typesafe.sbt"        % "sbt-multi-jvm" % "0.4.0")
