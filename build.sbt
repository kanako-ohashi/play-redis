name := "play-modules-redis"
organization := "jp.co.bizreach"

crossScalaVersions := Seq(scalaVersion.value, "2.13.0-M5")
scalaVersion := "2.12.8"

javacOptions ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint:unchecked", "-encoding", "UTF-8")
scalacOptions += "-deprecation"

libraryDependencies ++= Seq(
  "com.typesafe.play"         %% "play"               % "2.7.0"    % "provided",
  "com.typesafe.play"         %% "play-cache"         % "2.7.0",
  "com.typesafe.play"         %% "play-test"          % "2.7.0"    % "test",
  "com.typesafe.play"         %% "play-specs2"        % "2.7.0"    % "test",
  "biz.source_code"           %  "base64coder"        % "2010-12-19",
  "redis.clients"             %  "jedis"              % "3.0.1"
)

publishMavenStyle := true
publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (version.value.trim.endsWith("SNAPSHOT"))
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}
publishArtifact in Test := false
pomIncludeRepository := { _ => false }
pomExtra := {
  <scm>
    <url>https://github.com/bizreach/play-redis</url>
    <connection>scm:git:git@github.com:bizreach/play-redis.git</connection>
  </scm>
  <developers>
    <developer>
      <id>typesafe</id>
      <name>Typesafe</name>
      <url>https://typesafe.com</url>
    </developer>
    <developer>
      <id>takezoe</id>
      <name>Naoki Takezoe</name>
      <url>https://github.com/takezoe</url>
    </developer>
  </developers>
}
homepage := Some(url(s"https://github.com/bizreach/play-redis"))
licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.html"))
