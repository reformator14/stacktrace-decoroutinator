-dontwarn edu.umd.cs.findbugs.annotations.**
-dontwarn java.**
-dontwarn javax.**
-dontwarn reactor.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn android.test.AndroidTestCase
-dontwarn android.test.InstrumentationTestCase

-keepclasseswithmembers class * {
    @org.junit.Test <methods>;
    <init>();
}
-keepclasseswithmembers class * {
    @org.junit.Before <methods>;
    <init>();
}
-keepclasseswithmembers class * {
    @org.junit.Rule <methods>;
    <init>();
}
-keep class androidx.test.runner.AndroidJUnitRunner { *; }
