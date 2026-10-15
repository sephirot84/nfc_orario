#!/usr/bin/env bash
# Source this file before development commands in this cloud workspace.
export NFC_TOOLS_ROOT=${NFC_TOOLS_ROOT:-/workspace/.tools}
export JAVA_HOME=$(cat "$NFC_TOOLS_ROOT/jdk-path")
export ANDROID_HOME="$NFC_TOOLS_ROOT/android-sdk"
export ANDROID_USER_HOME=/workspace/.android
export ANDROID_PREFS_ROOT=/workspace
export GRADLE_USER_HOME=/workspace/.gradle
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
