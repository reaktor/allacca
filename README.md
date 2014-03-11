allacca
=======

Check the [UI design draft](doc/ui-design-draft.md "UI design draft").

Working with Idea
-----------------

* ./sbt gen-idea
* Remove full target from Excluded folders
* Mark all folders under target as excluded except target/android-gen
* Mark target/android-gen as source folder
* Mark src/test/scala as test source folder
* Add android lib from your SDK installation path as library (if it's not already). For example: /Applications/adt-bundle-mac-x86_64-20131030/sdk/platforms/android-19/android.jar

Licensing
---------

GPL v3 (see LICENSE.txt), copyright by the authors.

