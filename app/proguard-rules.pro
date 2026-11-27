# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Preserve source file and line numbers for clear crash stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,Signature,InnerClasses,EnclosingMethod

# Retrofit & QuranComApiService
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep interface com.example.core.network.QuranComApiService { *; }
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Moshi codegen & reflection fallback for network DTOs
-keep @com.squareup.moshi.JsonClass class * { *; }
-keep class com.example.core.network.*Dto { *; }
-keep class com.example.core.network.*JsonAdapter { *; }
-keepclasseswithmembers class * {
    @com.squareup.moshi.Json <fields>;
}

# Room entities, DAOs, and projections
-keep class * extends androidx.room.RoomDatabase
-keep class com.example.core.database.*Entity { *; }
-keep class com.example.core.database.*Projection { *; }

# Jetpack Glance & WorkManager reflection constructors
-keep class * implements androidx.glance.appwidget.action.ActionCallback { public <init>(); }
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { public <init>(); }
-keep class * extends androidx.work.CoroutineWorker { public <init>(...); }
-keep class * extends androidx.work.Worker { public <init>(...); }

# Suppress optional platform warnings from OkHttp / Conscrypt
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
