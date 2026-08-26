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

# Razorpay Checkout — the SDK reflects into the host Activity's payment callbacks,
# so they must survive shrinking or release builds crash on payment result.
-keepclassmembers class * {
    public void onPaymentSuccess(java.lang.String);
    public void onPaymentSuccess(java.lang.String, com.razorpay.PaymentData);
    public void onPaymentError(int, java.lang.String);
    public void onPaymentError(int, java.lang.String, com.razorpay.PaymentData);
}
-keep class com.razorpay.** { *; }
-dontwarn com.razorpay.**
-optimizations !method/inlining/*
