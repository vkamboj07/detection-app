# Keep Room entities
-keep class com.example.billboardanalytics.data.** { *; }

# Keep Gson serialization/deserialization
-keep class com.example.billboardanalytics.data.Observation { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep MPAndroidChart
-keep class com.github.mikephil.charting.** { *; }
