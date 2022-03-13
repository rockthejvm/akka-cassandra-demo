lazy val akkaHttpVersion = "10.2.8"
lazy val akkaVersion     = "2.6.9"
lazy val circeVersion    = "0.14.1"

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      scalaVersion    := "2.13.4"
    )),
    name := "akka-cassandra-demo",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http"                  % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-actor-typed"           % akkaVersion,
      "com.typesafe.akka" %% "akka-stream"                % akkaVersion,
      "com.typesafe.akka" %% "akka-persistence-typed"     % akkaVersion,
      "com.datastax.oss"  %  "java-driver-core"           % "4.13.0",   // See https://github.com/akka/alpakka/issues/2556
      "com.typesafe.akka" %% "akka-persistence-cassandra" % "1.0.5",
      "io.circe"          %% "circe-core"                 % circeVersion,
      "io.circe"          %% "circe-generic"              % circeVersion,
      "io.circe"          %% "circe-parser"               % circeVersion,
      "de.heikoseeberger" %% "akka-http-circe"            % "1.39.2",
      "ch.qos.logback"    % "logback-classic"             % "1.2.10",

      "com.typesafe.akka" %% "akka-http-testkit"          % akkaHttpVersion % Test,
      "com.typesafe.akka" %% "akka-actor-testkit-typed"   % akkaVersion     % Test,
      "org.scalatest"     %% "scalatest"                  % "3.2.9"         % Test
    )
  )
