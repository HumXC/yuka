#!/usr/bin/env sh
adb push ./app/build/outputs/apk/debug/app-debug.apk /data/local/tmp/yuka &&\
./run.sh