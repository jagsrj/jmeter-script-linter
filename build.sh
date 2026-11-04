#!/usr/bin/env bash
# Offline build (no Maven / internet needed): compiles, runs the self tests and packages the jar.
# Usage: ./build.sh            -> target/jmx-lint-<version>.jar
set -euo pipefail
cd "$(dirname "$0")"
VERSION="$(sed -n 's:.*<version>\(.*\)</version>.*:\1:p' pom.xml | head -1)"
OUT=target
rm -rf "$OUT" && mkdir -p "$OUT/stubs" "$OUT/classes" "$OUT/test-classes"
JAVAC="javac --release 8 -Xlint:all,-options,-serial -Werror -encoding UTF-8"

echo "== compiling JMeter API stub (compile-only, not packaged)"
$JAVAC -d "$OUT/stubs" $(find src/stubs/java -name '*.java')

echo "== compiling plugin"
$JAVAC -cp "$OUT/stubs" -d "$OUT/classes" $(find src/main/java -name '*.java')
cp -r src/main/resources/. "$OUT/classes/"

echo "== running self tests"
$JAVAC -cp "$OUT/classes:$OUT/stubs" -d "$OUT/test-classes" $(find src/test/java -name '*.java')
java -cp "$OUT/classes:$OUT/stubs:$OUT/test-classes" io.github.jagsrj.jmxlint.SelfTest src/test/resources

echo "== packaging"
printf 'Main-Class: io.github.jagsrj.jmxlint.cli.JmxLintCli\nImplementation-Title: jmx-lint\nImplementation-Version: %s\n' "$VERSION" > "$OUT/MANIFEST.MF"
jar cfm "$OUT/jmx-lint-$VERSION.jar" "$OUT/MANIFEST.MF" -C "$OUT/classes" .
echo "Built $OUT/jmx-lint-$VERSION.jar"
