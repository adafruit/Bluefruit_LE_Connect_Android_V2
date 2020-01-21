# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /Users/antonio/Library/Android/sdk/tools/proguard/proguard-android.txt
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

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile

# Remove logs on release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
 }

# Keep anonymous class declaration
-keepattributes EnclosingMethod

# Nordic DFU library
-keep class no.nordicsemi.android.dfu.** { *; }

# Paho library logger
-keep class org.eclipse.paho.client.mqttv3.logging.JSR47Logger {
    *;
}

# Avoid warnings for old code in Paho 1.0.2 on Android Studio 2
-keep class org.eclipse.paho.client.mqttv3.persist.** { *; }
-dontwarn org.eclipse.paho.client.mqttv3.persist.**
-keepattributes Exceptions, Signature, InnerClasses

# ImageMagick
-keep class magick.** { *; }