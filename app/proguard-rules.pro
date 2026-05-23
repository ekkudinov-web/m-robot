-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.minfinrobot.**$$serializer { *; }
-keepclassmembers class com.minfinrobot.** { *** Companion; }
-keepclasseswithmembers class com.minfinrobot.** {
    kotlinx.serialization.KSerializer serializer(...);
}
