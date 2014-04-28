// so we can use keywords from Android, such as 'Android' and 'proguardOptions'
import android.Keys._

// load the android plugin into the build
android.Plugin.androidBuild

name := "allacca"

version := "0.1-SNAPSHOT"

scalaVersion:= "2.11.0"

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-language:implicitConversions")

libraryDependencies ++= Seq(
  "joda-time"         % "joda-time"           % "2.3",
  "org.joda"          % "joda-convert"        % "1.4",
  "org.scalacheck" %% "scalacheck" % "1.11.3" % "test",
  "org.scalatest" %% "scalatest" % "2.1.4" % "test",
  "junit" % "junit" % "4.10" % "test"
)

// for non-ant-based projects, you'll need this for the specific build target:
// SDK version goes here
// KitKat       4.4 - 4.4.2     API level 19
// http://source.android.com/source/build-numbers.html
// However, something related is configured in ./src/main/AndroidManifest.xml
platformTarget in Android := "android-19"

// call install and run without having to prefix with android:
run <<= run in Android

install <<= install in Android

proguardCache in Android ++= Seq(
  ProguardCache("org.joda") % "joda-time" % "joda-time",
  ProguardCache("org.joda.convert") % "org.joda" % "joda-convert"
)

debugIncludesTests in Android := true

apkbuildExcludes in Android ++= Seq(
  "META-INF/LICENSE.txt",
  "META-INF/NOTICE.txt"
)
