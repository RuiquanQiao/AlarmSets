# R8 rules for the release build.
#
# Anything that is looked up by name at runtime rather than called directly has
# to be kept by hand. In this app that means the serialization layer and the
# platform components Android instantiates itself.

# ---------------------------------------------------------------------------
# kotlinx.serialization
#
# Serializers are generated as companion objects and nested $$serializer
# classes, then found reflectively. Without these the template export/import
# and the stored RingtoneRef column fail at runtime, not at build time - which
# is exactly the kind of bug that only shows up in a release build.
# ---------------------------------------------------------------------------
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations, AnnotationDefault

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    *** Companion;
}

-keepclasseswithmembers class ** {
    @kotlinx.serialization.Serializable <fields>;
}

# The sealed RingtoneRef hierarchy is serialised polymorphically by its
# @SerialName discriminator, so every subtype must survive.
-keep,includedescriptorclasses class io.github.ruiquanqiao.alarmsets.core.model.**$$serializer { *; }
-keep class io.github.ruiquanqiao.alarmsets.core.model.RingtoneRef { *; }
-keep class io.github.ruiquanqiao.alarmsets.core.model.RingtoneRef$* { *; }

# Domain and template DTOs are pure data crossing the serialization boundary.
-keep class io.github.ruiquanqiao.alarmsets.core.model.** { *; }
-keep class io.github.ruiquanqiao.alarmsets.core.domain.AlarmSetTemplate { *; }
-keep class io.github.ruiquanqiao.alarmsets.core.domain.TemplateAlarm { *; }

# The GitHub release payload is decoded straight into these.
-keep class io.github.ruiquanqiao.alarmsets.core.update.** { *; }

# ---------------------------------------------------------------------------
# Room
# ---------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# ---------------------------------------------------------------------------
# Components Android constructs by name from the manifest.
# ---------------------------------------------------------------------------
-keep class io.github.ruiquanqiao.alarmsets.core.alarm.AlarmReceiver { *; }
-keep class io.github.ruiquanqiao.alarmsets.core.alarm.BootReceiver { *; }
-keep class io.github.ruiquanqiao.alarmsets.core.alarm.AlarmRingService { *; }
-keep class io.github.ruiquanqiao.alarmsets.AlarmSetsApplication { *; }
-keep class io.github.ruiquanqiao.alarmsets.MainActivity { *; }
-keep class io.github.ruiquanqiao.alarmsets.ring.RingActivity { *; }

# ---------------------------------------------------------------------------
# OkHttp ships its own rules; these silence the optional dependencies it
# references but does not need.
# ---------------------------------------------------------------------------
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Keep line numbers so a crash report from a user is actually readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
