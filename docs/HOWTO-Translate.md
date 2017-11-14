# How To Translate

To translate 'Media Merger' to your language you need to translate following files:

  * [strings.xml](/app/src/main/res/values/strings.xml) if the same file in an upper folder `values-*` is not transated
  * [about.html](/app/src/main/res/raw/about.html) if the same file in an upper folder `raw-*` is not transated

Additional file from 'android-library'
  * [https://gitlab.com/axet/android-library/.../strings.xml](https://gitlab.com/axet/android-library/blob/master/src/main/res/values/strings.xml) if the same file in an upper folder `values-*` is not transated

Also, add Google Play translation for:
  * Title (50 symbols max.)
  * Short description (80 symbols max.)
  * Full description (4000 symbols max.)

Then add those files to the repository using "New Issue" or "Merge Request" (GitLab's name for "Pull Request" if you come from GitHub) against the `dev` branch.