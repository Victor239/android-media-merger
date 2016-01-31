# android-media-merger

Utitily moves /sdcard/DCIM/* and /sdcard/Pictures/Screenshots to user specified folder /sdcard/Media

Can be synced /sdcard/Media with https://github.com/syncthing/syncthing-android

Keeps pictures in one folder using nice name format [2015-01-01 13.11.59.png].

[Camera.java](/app/src/main/java/com/github/axet/mover/Camera.java)

Android friendly. Application waits for event from ContentObserver, then scan for new files.

# Install

    ./gradlew installDebug

# Screenshots

![shot1](/docs/shot1.png)
