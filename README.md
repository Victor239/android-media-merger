# android-media-merger

  * Utitily moves `/sdcard/DCIM/*]` and `[/sdcard/Pictures/Screenshots/*]` to user specified folder.
  * All files will be renamed to `[2015-01-01 13.11.59.png]`.

Main app logic is here:

  * [Camera.java](/app/src/main/java/com/github/axet/mover/Camera.java)

Android friendly. Application waits for event from ContentObserver, then scan for new files.

# Install

[![ Google Play](docs/google-play-badge.png)](https://play.google.com/store/apps/details?id=com.github.axet.mover) 

Manual install

    ./gradlew installDebug

# Screenshots

![shot1](/docs/shot1.png)
