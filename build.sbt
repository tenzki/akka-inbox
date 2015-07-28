name := "akka-inbox"

version := "1.0"

scalaVersion := "2.11.7"

javacOptions ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint")

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.12",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.12" % "test",
  "com.typesafe.akka" % "akka-persistence-experimental_2.11" % "2.3.12",
  "com.github.scullxbones" %% "akka-persistence-mongo-rxmongo" % "0.4.1",
  "org.reactivemongo" % "reactivemongo_2.11" % "0.11.4",
  "org.mongodb" % "mongodb-driver" % "3.0.3" % "test",
  "junit" % "junit" % "4.12" % "test",
  "com.novocode" % "junit-interface" % "0.11" % "test")

initialize := {
  val _ = initialize.value
  if (sys.props("java.specification.version") != "1.8")
    sys.error("Java 8 is required for this project.")
}
