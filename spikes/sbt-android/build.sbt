
androidDefaults

name := "Hello Sbt Android"

version := "0.1-SNAPSHOT"

versionCode := 0

scalaVersion:= "2.11.0-M7"

// Looks like the plugin uses an implicit conversion
// "See the Scala docs for value scala.language.implicitConversions for a discussion
// why the feature should be explicitly enabled."
scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-language:implicitConversions")

// SDK version goes here
// KitKat 	4.4 - 4.4.2 	API level 19
// http://source.android.com/source/build-numbers.html
// However, something related is configured in ./src/main/AndroidManifest.xml
platformName := "android-19"

// See for Proguard options
// http://fxthomas.github.io/android-plugin/tutorial/02-configuring-the-build.html#toc_8
usePreloaded := true

useProguard := false
