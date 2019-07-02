resolvers += Resolver.bintrayRepo("bbp", "nexus-releases")

addSbtPlugin("ch.epfl.bluebrain.nexus" % "sbt-nexus" % "0.11.0")
addSbtPlugin("pl.project13.scala"      % "sbt-jmh"   % "0.3.4")
