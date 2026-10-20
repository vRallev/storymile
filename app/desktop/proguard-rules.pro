# OkHttp probes these optional TLS providers and GraalVM integrations at runtime.
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn org.graalvm.nativeimage.hosted.Feature*
-dontwarn com.oracle.svm.core.annotate.*

# Kotlin Inject Anvil has optional JSR-330 annotations for Dagger compatibility.
-dontwarn javax.inject.Qualifier
-dontwarn javax.inject.Scope

# Prevent return-type specialization from breaking Okio's multifile facade.
-keep,allowshrinking,allowobfuscation class okio.Okio__* { *; }

# Compose Desktop does not read Coil's META-INF/proguard rules.
-keep class coil3.util.DecoderServiceLoaderTarget { *; }
-keep class coil3.util.FetcherServiceLoaderTarget { *; }
-keep class coil3.util.ServiceLoaderComponentRegistry { *; }
-keep class * implements coil3.util.DecoderServiceLoaderTarget { *; }
-keep class * implements coil3.util.FetcherServiceLoaderTarget { *; }

# Ktor loads HTTP engines through ServiceLoader and updates volatile fields by name.
-keep class io.ktor.client.engine.** implements io.ktor.client.HttpClientEngineContainer
-keepclassmembers class io.ktor.** {
    volatile <fields>;
}
-keepclassmembernames class io.ktor.** {
    volatile <fields>;
}

# DataStore's Protobuf runtime reads message fields by name.
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}
