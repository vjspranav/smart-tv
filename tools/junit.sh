#!/bin/sh
# Compiles the launcher's pure-Java model and its JUnit tests on the Mac and runs them.
# Usage: tools/junit.sh      (fetches junit/hamcrest into tools/lib on first run)
set -e
root="$(cd "$(dirname "$0")/.." && pwd)"
lib="$root/tools/lib"; mkdir -p "$lib"
M=https://repo1.maven.org/maven2
[ -f "$lib/junit.jar" ] || curl -sfL -o "$lib/junit.jar" "$M/junit/junit/4.13.2/junit-4.13.2.jar"
[ -f "$lib/hamcrest.jar" ] || curl -sfL -o "$lib/hamcrest.jar" "$M/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar"
export JAVA_HOME="${JAVA_HOME:-$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home}"
out="$(mktemp -d)"; trap 'rm -rf "$out"' EXIT
cd "$root/launcher"  # relative paths: the project path contains spaces
cp="$lib/junit.jar:$lib/hamcrest.jar"
"$JAVA_HOME/bin/javac" -Xlint:-options --release 8 -cp "$cp" -d "$out" $(find src -path '*/model/*.java') $(find test -name '*.java')
classes=$(cd test && find . -name '*Test.java' | sed 's|^\./||; s|\.java$||; s|/|.|g')
"$JAVA_HOME/bin/java" -cp "$out:$cp" org.junit.runner.JUnitCore $classes
