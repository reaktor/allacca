// so we can use keywords from Android, such as 'Android' and 'proguardOptions'
import android.Keys._

// load the android plugin into the build
android.Plugin.androidBuild

name := "android-sdk-spike"

version := "0.1-SNAPSHOT"

scalaVersion:= "2.11.0-M8"

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-language:implicitConversions")

libraryDependencies += "com.github.nscala-time" %% "nscala-time" % "0.8.0"

// for non-ant-based projects, you'll need this for the specific build target:
// SDK version goes here
// KitKat       4.4 - 4.4.2     API level 19
// http://source.android.com/source/build-numbers.html
// However, something related is configured in ./src/main/AndroidManifest.xml
platformTarget in Android := "android-19"

// call install and run without having to prefix with android:
run <<= run in Android

install <<= install in Android

