-dontobfuscate

# Ignore missing JSR-305 annotations (compile-time only)
-dontwarn javax.annotation.**
-dontnote javax.annotation.**

# Keep rules for JSoup and Apache Tika dependencies
-dontwarn org.apache.tika.**
-dontwarn org.jsoup.**

# Ignore missing service files
-dontwarn META-INF.services.**

