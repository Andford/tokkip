# Annotations some libraries (Guava) reference but never need at runtime.
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
-dontwarn afu.org.checkerframework.**

# Tokkip's assistant: both engines reach into their Kotlin classes from native
# code by name, so R8 must keep them as they are.
-keep class com.google.ai.edge.litertlm.** { *; }
-keep class com.k2fsa.sherpa.onnx.** { *; }
-dontwarn com.google.ai.edge.litertlm.**
# commons-compress: only bzip2 and tar are used; the rest pulls optional libraries.
-dontwarn org.apache.commons.compress.**
-dontwarn org.brotli.dec.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.tukaani.xz.**
-dontwarn org.objectweb.asm.**
