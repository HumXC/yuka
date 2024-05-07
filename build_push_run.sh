#!/usr/bin/env sh
./gradlew assembleDebug && ./push_run.sh "$@"