#!/bin/sh
# Run one of examples/*.java against the SDK built from this checkout.
#
#   scripts/run-example.sh ResolveName "mark hoppus"
#
# Builds the SDK once (./mvnw package, tests skipped) and writes its dependency classpath to target/classpath.txt, then
# runs the example as a single-file Java program, which needs no separate compile step.
set -e
cd "$(dirname "$0")/.."
example="$1"; shift
if [ ! -f target/classpath.txt ] || [ ! -d target/classes ]; then
  ./mvnw -q -DskipTests package dependency:build-classpath -Dmdep.outputFile=target/classpath.txt >&2
fi
exec java -cp "target/classes:$(cat target/classpath.txt)" "examples/$example.java" "$@"
