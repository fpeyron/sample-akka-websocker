import sbt.{AutoPlugin, Setting}
import sbtrelease.ReleasePlugin
import sbtrelease.ReleasePlugin.autoImport.{ReleaseStep, releaseProcess}
import sbtrelease.ReleaseStateTransformations._

object SbtGitFlowReleasePlugin extends AutoPlugin {

  private val releaseSteps = Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireVersions,
    SbtGitFlowReleaseCommand.initFlow,
    runClean,
    runTest,
    setReleaseVersion,
    commitReleaseVersion,
    tagRelease,
    SbtGitFlowReleaseCommand.mergeFlow,
    setNextVersion,
    commitNextVersion,
    publishArtifacts,
    SbtGitFlowReleaseCommand.publishDocker,
    SbtGitFlowReleaseCommand.pushChanges
  )

  override def requires = ReleasePlugin

  override def trigger = allRequirements

  override lazy val projectSettings = Seq[Setting[_]](releaseProcess := releaseSteps)
}
