# Retrofit
-keepattributes Signature, InnerClasses, AnnotationDefault
-keepclassmembers,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Moshi
-keepclassmembers class * {
    @com.squareup.moshi.Json *;
}

# App Data Models - Essential for JSON parsing (Moshi)
-keep class com.kks.bharatkirana.data.model.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# OkHttp
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn okhttp3.**

# Firebase
-keep class com.google.firebase.** { *; }

# Coil
-keep class coil.** { *; }
-dontwarn coil.**
