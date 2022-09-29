# Media Merger

Android OS does not allow you to create symbolic links on /sdcard. It does not allow you to change Camera and Screenshots folders location. The only way to put all content created by phone into one place, is to manually move those files into one folder manually. Or create an app which will automate the process.

If you want also store SMS as text files (or reply to SMS using text files) into same folder please check [SMS Gate](https://gitlab.com/axet/android-sms-gate)

Use this app in conjunction with Syncthing to sync / backup files produced by your phone to your computer to manually delete / arrange files.

  * This utitily moves all files from `[/sdcard/DCIM/*/*]` and `[/sdcard/Pictures/Screenshots/*]` to user specified folder.
  * Using two pass scan algorithm (prevent's move during file changes)
  * All files will be renamed related to it's date `[2015-12-31 13.44.59.png]`.

Main app logic is here:

  * [Camera.java](/app/src/main/java/com/github/axet/mover/app/Camera.java)

Android friendly. Application waits for event from ContentObserver, then scan for new files.

# Manual install

    gradle installDebug

# Translate

If you want to translate 'Media Merger' to your language please read this:

  * [HOWTO-Translate.md](/docs/HOWTO-Translate.md)

# Screenshots

![shot](/docs/shot1.png)

# Contributors

  * German translation thanks to @DJCrashdummy

# Links

  * https://syncthing.net
