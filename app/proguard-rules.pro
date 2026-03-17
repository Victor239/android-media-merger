-dontobfuscate

# Ignore warnings about missing annotation classes
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
-dontwarn com.google.errorprone.**

# Keep JSoup classes
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

# Keep Apache Tika service references
-dontwarn org.apache.tika.**

# Keep commons-io classes
-keep class org.apache.commons.io.** { *; }

# Suppress warnings about missing service classes
-dontwarn META-INF.services.**
