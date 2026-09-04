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
-keep class dev.reformator.stacktracedecoroutinator.** { *; }
