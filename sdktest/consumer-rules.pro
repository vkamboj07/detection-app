# Keep SDK public API entry points
-keep class com.footfallanalytics.sdk.FootfallAnalyticsSDK { *; }
-keep class com.footfallanalytics.sdk.SDKConfig { *; }
-keep class com.footfallanalytics.sdk.SDKConfig$Builder { *; }
-keep class com.footfallanalytics.sdk.FootfallListener { *; }

# Keep SDK model classes (returned by public API)
-keep class com.footfallanalytics.sdk.model.FootfallMetrics { *; }
-keep class com.footfallanalytics.sdk.model.Observation { *; }

# Keep Room entities for Gson serialization
-keep class com.footfallanalytics.sdk.data.** { *; }

# Keep Gson serialization
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
