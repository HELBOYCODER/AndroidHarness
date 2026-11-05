#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
# ponytail: pure JVM smoke avoids Android SDK/native builds; use kotlinc or Gradle's bundled compiler.
SOURCES="app/src/main/java/com/androidharness/app/harness/ExternalHarness.kt scripts/external-harness-smoke/HarnessSmoke.kt"
if command -v kotlinc >/dev/null 2>&1; then
    kotlinc $SOURCES -include-runtime -d "$OUT/smoke.jar"
    java -jar "$OUT/smoke.jar"
else
    if [ -z "${KOTLIN_COMPILER_LIB:-}" ]; then
        echo 'Set KOTLIN_COMPILER_LIB to a Gradle distribution lib directory, or install kotlinc.' >&2
        exit 2
    fi
    STDLIB=
    for jar in "$KOTLIN_COMPILER_LIB"/kotlin-stdlib-[0-9]*.jar; do
        [ -f "$jar" ] && STDLIB="$jar" && break
    done
    [ -n "$STDLIB" ] || { echo 'Kotlin stdlib jar not found.' >&2; exit 2; }
    java -cp "$KOTLIN_COMPILER_LIB/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
        -no-stdlib -no-reflect -classpath "$STDLIB" \
        -d "$OUT/classes" $SOURCES
    java -cp "$OUT/classes:$KOTLIN_COMPILER_LIB/*" com.androidharness.app.harness.HarnessSmokeKt
fi
