import sbt.Keys._
import sbt._
import sbtrelease.ReleasePlugin.autoImport.{ReleaseKeys, ReleaseStep}

// imports standard command parsing functionality

object ReleaseCommand {
  // A simple, no-argument command that prints "Hi",
  //  leaving the current state unchanged.
  def hello = Command.command("hello") { state =>
    println("Hi!")
    state
  }


  lazy val runTest: ReleaseStep = ReleaseStep(
    action = { st: State =>
      if (!st.get(ReleaseKeys.skipTests).getOrElse(false)) {
        val extracted = Project.extract(st)
        val ref = extracted.get(thisProjectRef)
        extracted.runAggregated(test in Test in ref, st)
      } else st
    },
    enableCrossBuild = true
  )


  lazy val initFlow: ReleaseStep = ReleaseStep(
    action = { st: State =>

      // 1. Synchronisation avec remote
      "git fetch -p origin".! match {
        case 0 => // do nothing
        case _ => sys.error("cannot fetch with origin repository")
      }

      // 2. Check unstagged file on master branch
      "git diff master --quiet master".! match {
        case 0 => // do nothing
        case _ => sys.error("Fails because some file are unstagged on master branch!")
      }

      // 3. Check unstagged file on staging branch
      "git diff staging --quiet staging".! match {
        case 0 => // do nothing
        case _ => sys.error("Fails because some file are unstagged on staging branch!")
      }

      // 4. Check behind file on master branch
      Process("rev-list master..origin/master --count").!! match {
        case "0" => // do nothing
        case _ => sys.error("Fails because some commit are behing on master branch!")
      }

      // 5. Check behind file on staging branch
      Process("rev-list staging..origin/staging --count").!! match {
        case "0" => // do nothing
        case _ => sys.error("Fails because some commit are behing on staging branch!")
      }

      /*
      // 3. Check master
      "git diff master --quiet master origin/master".! match {
        case 0 => // do nothing
        case _ => sys.error("release failed because some commits are unsynchronized on master branch!")
      }

      // 3. Check master
      "git diff master --quiet staging origin/staging".! match {
        case 0 => // do nothing
        case _ => sys.error("release failed because some commits are unsynchronized on staging branch!")
      }
      */

      // 5. Create or reset release branch
      val version = st.get(ReleaseKeys.versions).map(_._1).getOrElse(sys.error("No versions are set! Was this release part executed before inquireVersions?"))
      s"git checkout release/$version".! match {
        case 0 => // do nothing
        case _ => s"git checkout -b release/$version staging".! match {
          case 0 => // do nothing
          case _ => sys.error(s"failure on creating release branch release/$version!")
        }
      }

      st
    },
    enableCrossBuild = false
  )

  lazy val mergeFlow: ReleaseStep = ReleaseStep(
    action = { st: State =>
      val version = st.get(ReleaseKeys.versions).map(_._1).getOrElse(sys.error("No versions are set! Was this release part executed before inquireVersions?"))

      // Checkout on master branch
      s"git checkout master".! match {
        case 0 => // do nothing
        case _ => sys.error("failure to checkout on master!")
      }

      // 5. Merge release to master
      s"git merge release/$version".! match {
        case 0 => // do nothing
        case _ => sys.error(s"failure on creating release branch release/$version!")
      }

      // Checkout on staging branch
      s"git checkout staging".! match {
        case 0 => // do nothing
        case _ => sys.error("failure to checkout on staging!")
      }

      // 5. Merge release to staging
      s"git merge release/$version".! match {
        case 0 => // do nothing
        case _ => sys.error(s"failure on creating release branch release/$version!")
      }

      // Remove release branch
      s"git branch -d release/$version".! match {
        case 0 => // do nothing
        case _ => sys.error(s"failure on creating release branch release/$version!")
      }

      st
    },
    enableCrossBuild = false
  )

  lazy val pushChanges: ReleaseStep = ReleaseStep(
    action = { st: State =>
      val version = st.get(ReleaseKeys.versions).map(_._1).getOrElse(sys.error("No versions are set! Was this release part executed before inquireVersions?"))

      // Checkout on master branch
      s"git push origin master staging".! match {
        case 0 => // do nothing
        case _ => sys.error("failure to checkout on master!")
      }

      s"git push origin :release/$version 2>/dev/null"


      // 5. Merge release to master
      s"git push --tag -f".! match {
        case 0 => // do nothing
        case _ => sys.error(s"failure on creating release branch release/$version!")
      }

      st
    },
    enableCrossBuild = false
  )
}