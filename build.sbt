import im.dlg.SbtDialogDistributor._

enablePlugins(SbtDialogDistributor)

organization := "im.dlg"

name := "dialog-repost"

sdkVersion := detectSdkVersion("1.13.0")

version := versionWithSdk("0.0.2", sdkVersion.value)

scalaVersion := "2.11.11"

resolvers += "krasserm at bintray" at "http://dl.bintray.com/krasserm/maven"

libraryDependencies += "org.twitter4j" % "twitter4j-stream" % "4.0.6"

licenses += ("Apache-2.0", url(
  "https://www.apache.org/licenses/LICENSE-2.0.html"))

publishMavenStyle := true

bintrayOrganization := Some("dialog")

bintrayRepository := "dialog"

bintrayOmitLicense := true

DialogHouseRules.defaultDialogSettings
