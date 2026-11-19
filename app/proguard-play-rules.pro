# Play flavor only — ML Kit is not on the foss classpath.
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**
