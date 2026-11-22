#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT
sources=(app/src/main/java/com/ghostaku/arcade/Games.java tests/GamesTest.java)
if [[ -n ${ECJ_JAR:-} ]]; then
  java -jar "$ECJ_JAR" -8 -encoding UTF-8 -d "$out" "${sources[@]}"
else
  javac -encoding UTF-8 -d "$out" "${sources[@]}"
fi
java -cp "$out" com.ghostaku.arcade.GamesTest
