import im.dlg.DialogHouseRules

enablePlugins(SbtDialogDistributor)

organization := "im.dlg"

name := "dialog-repost"

sdkVersion := "0.1.2-SNAPSHOT"

version := "0.0.1-sdk_" + sdkVersion.value.replace("-SNAPSHOT", "")

scalaVersion := "2.11.8"

resolvers += "krasserm at bintray" at "http://dl.bintray.com/krasserm/maven"

libraryDependencies += "org.twitter4j" % "twitter4j-stream" % "4.0.6"

licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html"))

publishMavenStyle := true

bintrayOrganization := Some("dialog")

bintrayRepository := "dialog"

bintrayOmitLicense := true

DialogHouseRules.defaultDialogSettings
