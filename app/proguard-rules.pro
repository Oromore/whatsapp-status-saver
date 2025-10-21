# Add project specific ProGuard rules here.

# Unity Ads - Keep all Unity classes
-keep class com.unity3d.ads.** { *; }
-keep class com.unity3d.services.** { *; }
-dontwarn com.unity3d.ads.**
-dontwarn com.unity3d.services.**

# Keep Unity Ads listeners
-keepclassmembers class * implements com.unity3d.ads.IUnityAdsLoadListener {
    public *;
}
-keepclassmembers class * implements com.unity3d.ads.IUnityAdsShowListener {
    public *;
}
-keepclassmembers class * implements com.unity3d.ads.IUnityAdsInitializationListener {
    public *;
}
-keepclassmembers class * implements com.unity3d.services.banners.BannerView$IListener {
    public *;
}

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

# ExoPlayer / Media3
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
