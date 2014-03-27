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


Releasing
---------

Set correct application version to AndroidManifest.xml

Make sure there are all logging statements go via our own ````Logger```` object which
does not invoke Android ````Log```` except in debug mode.

Create application key e.g. like this

    keytool -genkey -v -alias allacca  -keysize 1024 -validity 11586 \
    -dname "CN=Timo Rantalaiho, OU=Unknown, O=Allacca, L=Helsinki, ST=Unknown, C=FI"

Configure access to the relevant keystore in ````local.properties```` , e.g. something like this:

    key.alias: allacca
    key.store: /home/my_account/.keystore
    key.store.password: my_password

Start sbt console

    ./sbt

and enter something like

    ;reload;clean;android:package-release;android:signRelease;android:uninstall;android:run

If the application works well, it might be time to upload the APK to Play Store.

