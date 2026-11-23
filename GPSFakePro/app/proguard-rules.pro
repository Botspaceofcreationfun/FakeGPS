# GPS Fake Pro — ProGuard rules (release builds only)
# osmdroid is pure Java; keep its public API used reflectively via XML config.
-keep class org.osmdroid.** { *; }
-keep interface org.osmdroid.** { *; }
