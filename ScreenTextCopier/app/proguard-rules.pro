# Add project specific ProGuard rules here.

# ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.screentextcopier.model.** { *; }
