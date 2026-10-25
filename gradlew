#!/bin/sh
export GRADLE_USER_HOME="$(dirname "$0")/.gradle-home"
exec "$(dirname "$0")/.gradle-dist/gradle-8.10.2/bin/gradle" "$@"
