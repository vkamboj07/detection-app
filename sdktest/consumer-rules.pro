# Keep Room entities for Gson serialization
-keep class com.footfallanalytics.sdk.data.** { *; }

# Keep SDK model classes
-keep class com.footfallanalytics.sdk.model.** { *; }

# Keep Gson serialization
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
