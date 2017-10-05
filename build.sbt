import sbt.Keys.mainClass

organization := "io.newsbridge.sample"
name := "sample-akka-http-docker"

scalaVersion := "2.12.3"

val akkaVersion = "2.5.4"
val akkaHttpVersion = "10.0.9"


libraryDependencies += "com.typesafe.akka" %% "akka-http" % akkaHttpVersion
libraryDependencies += "com.typesafe.akka" %% "akka-parsing" % akkaHttpVersion
libraryDependencies += "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % akkaVersion
libraryDependencies += "com.typesafe.akka" %% "akka-stream" % akkaVersion
libraryDependencies += "com.typesafe.akka" %% "akka-slf4j" % akkaVersion

libraryDependencies += "com.github.swagger-akka-http" %% "swagger-akka-http" % "0.10.1"
libraryDependencies += "io.swagger" % "swagger-jaxrs" % "1.5.16"

// ----------------
dependencyOverrides += "com.typesafe.akka" %% "akka-stream" % akkaVersion
dependencyOverrides += "com.typesafe.akka" %% "akka-actor" % akkaVersion
// ----------------
// ----------------

// Docker packaging
enablePlugins(DockerPlugin, JavaAppPackaging)

packageName in Docker := name.value
version in Docker := version.value
maintainer in Docker := "contrib@newsbridge.io"
dockerBaseImage := "openjdk:latest"
dockerExposedPorts := Seq(8080)
dockerUpdateLatest := true

import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._
//addArtifact(Artifact("myProject", "assembly"), sbtassembly.AssemblyKeys.assembly)
//releaseProcess := Seq[ReleaseStep](
//  publishArtifacts
//)

lazy val dockerPrefix = "677537359471.dkr.ecr.eu-west-1.amazonaws.com/"
lazy val dockerMaintainer = "Newsbridge technical support <develop@newsbridge.io>"
lazy val dockerRootImage = "openjdk:8u141-jre-slim"


lazy val root = (project in file("."))

  // Ensures fat jar gets published too
  .settings(
  publishTo := Some(Resolver.file("file", new File(Path.userHome.absolutePath + "/.m2/repository"))),
  releaseTagComment := s"Releasing ${(version in ThisBuild).value}",
  releaseTagName := s"v-${if (releaseUseGlobalVersion.value) (version in ThisBuild).value else version.value}",
  mainClass in assembly := Some("io.newsbridge.sample.ApplicationMain"),
  mainClass in Compile := Some("io.newsbridge.sample.ApplicationMain"),
  addArtifact(Artifact("sample-akka-http-docker", "assembly"), sbtassembly.AssemblyKeys.assembly),
  releaseProcess := Seq[ReleaseStep](
    checkSnapshotDependencies, // : ReleaseStep
    inquireVersions, // : ReleaseStep
    runClean, // : ReleaseStep
    runTest, // : ReleaseStep
    setReleaseVersion, // : ReleaseStep
    commitReleaseVersion, // : ReleaseStep, performs the initial git checks
    tagRelease, // : ReleaseStep
    publishArtifacts, // : ReleaseStep, checks whether `publishTo` is properly set up
    setNextVersion, // : ReleaseStep
    commitNextVersion, // : ReleaseStep
    pushChanges // : ReleaseStep, also checks that an upstream branch is properly configured
  )
)
  .enablePlugins(DockerPlugin, JavaAppPackaging)
  .settings(
    packageName in Docker := s"${dockerPrefix}${name.value}",
    maintainer in Docker := dockerMaintainer,
    dockerBaseImage := dockerRootImage,
    dockerEntrypoint := Seq(s"bin/${name.value.toLowerCase}"),
    dockerExposedPorts := Seq(8180),
    dockerUpdateLatest := true
  )


// version master