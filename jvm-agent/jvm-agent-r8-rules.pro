# First attempt: pure tree-shaking, nothing else. The goal here is "does real shrinking happen
# without breaking anything," not maximal size reduction - renaming/inlining risk is not worth it
# against a codebase this reflection/MethodHandle/bytecode-generation-heavy.
-dontobfuscate
-dontoptimize
-keepattributes *Annotation*,InnerClasses,EnclosingMethod,Signature,SourceFile,LineNumberTable

# Everything this project owns, kept whole - it's a small fraction of the jar's total size, and
# nothing in it should be touched (name-sensitive: @MethodNameConstant-baked strings,
# ServiceLoader-instantiated SPI impls, reflection lookups throughout).
#noinspection ExpensiveKeepRuleInspection
-keep class dev.reformator.stacktracedecoroutinator.jvmagentjar.classtransformer.** { *; }
-keep class dev.reformator.stacktracedecoroutinator.jvmagentjar.generatorjvm.** { *; }
-keep class dev.reformator.stacktracedecoroutinator.jvmagentjar.jvmagent.** { *; }
-keep class dev.reformator.stacktracedecoroutinator.jvmagentjar.jvmagentcommon.** { *; }
-keep class dev.reformator.stacktracedecoroutinator.jvmagentjar.mhinvoker.** { *; }
-keep class dev.reformator.stacktracedecoroutinator.jvmagentjar.provider.** { *; }
-keep class dev.reformator.stacktracedecoroutinator.jvmagentjar.runtimesettings.** { *; }
-keep class dev.reformator.stacktracedecoroutinator.jvmagentjar.specmethodbuilder.** { *; }
