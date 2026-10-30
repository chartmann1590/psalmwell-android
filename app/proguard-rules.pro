-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-dontwarn org.conscrypt.**
-dontwarn com.google.android.gms.**
# Firestore documents are read field by field (no reflection-based mapping), so no model keep rules are needed.
# WorkManager instantiates workers by class name.
-keep class com.hartmannstudios.psalmwell.reminder.DailyVerseWorker { <init>(...); }
