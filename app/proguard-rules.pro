# kotlinx.serialization keeps generated serializers for @Serializable classes.
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.pooriapm.momentum.**$$serializer { *; }
-keepclassmembers class com.pooriapm.momentum.** {
    *** Companion;
}
-keepclasseswithmembers class com.pooriapm.momentum.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Poolakey talks to the Bazaar app over AIDL.
-keep class com.farsitel.bazaar.** { *; }
-keep class ir.cafebazaar.poolakey.** { *; }
