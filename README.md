# Media Merger

Android OS does not allow you to create symbolic links on /sdcard. It does not allow you to change Camera or Screenshots folder. The only way to put all content created by phone into one place, is to create application which helps combine all folders in one.

  * This utitily moves all files from `[/sdcard/DCIM/*/*]` and `[/sdcard/Pictures/Screenshots/*]` to user specified folder.
  * All files will be renamed related to it's date `[2015-12-31 13.44.59.png]`.

Main app logic is here:

  * [Camera.java](/app/src/main/java/com/github/axet/mover/app/Camera.java)

Android friendly. Application waits for event from ContentObserver, then scan for new files.

# Install

[![ Google Play](docs/google-play-badge.png)](https://play.google.com/store/apps/details?id=com.github.axet.mover) 

Manual install

    gradle installDebug

# Screenshots

![shot1](/docs/shot1.png)
