# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /Users/hemanths/Library/Android/sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-dontwarn java.lang.invoke.*
-dontwarn **$$Lambda$*
-dontwarn javax.annotation.**

# RetroFit
# Retrofit 2's real package is retrofit2.**, not retrofit.** - the rule below is a dead
# leftover from Retrofit 1.x and matched nothing. Kept (harmless) alongside the correct one
# rather than silently deleted, since removing a rule someone added on purpose deserves a
# reason on record, not just disappearing.
-dontwarn retrofit.**
-keep class retrofit.** { *; }
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Exceptions
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
 <init>(...);
}
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}
-keep class com.bumptech.glide.load.data.ParcelFileDescriptorRewinder$InternalRewinder {
  *** rewind();
}

# OkHttp
# com.squareup.okhttp3.** isn't a real package - OkHttp3 classes live directly under
# okhttp3.**. Same situation as the Retrofit rule above: kept, but fixed alongside it.
-keepattributes Signature
-keepattributes *Annotation*
-keep interface com.squareup.okhttp3.** { *; }
-dontwarn com.squareup.okhttp3.**
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Gson - needed for the lyrics network DTOs (network/lyrics/dto/**), most of which rely on
# plain field-name matching rather than @SerializedName, so their field names must survive
# R8's obfuscation or Gson silently fails to populate them (this was the actual bug behind
# "works in debug, empty results in release").
-keepattributes Signature
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keep class code.name.monkey.retromusic.network.lyrics.dto.** { *; }
-keepclassmembers class code.name.monkey.retromusic.network.lyrics.dto.** { *; }
-dontwarn org.jsoup.**

#-dontwarn
#-ignorewarnings

#Jaudiotagger
-dontwarn org.jaudiotagger.**
-dontwarn org.jcodec.**
-keep class org.jaudiotagger.** { *; }
-keep class org.jcodec.** { *; }

-keepclassmembers enum * { *; }
-keepattributes *Annotation*, Signature, Exception
-keepnames class androidx.navigation.fragment.NavHostFragment
-keep class * extends androidx.fragment.app.Fragment{}
-keepnames class * extends android.os.Parcelable
-keepnames class * extends java.io.Serializable
-keep class code.name.monkey.retromusic.network.model.** { *; }
-keep class code.name.monkey.retromusic.model.** { *; }
-keep class com.google.android.material.bottomsheet.** { *; }