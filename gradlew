#!/bin/sh

# Gradle wrapper script for AuthSecured

JAVACMD="java"
if [ -n "$JAVA_HOME" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
fi

WRAPPER_JAR="$(dirname "$0")/gradle/wrapper/gradle-wrapper.jar"

if [ ! -f "$WRAPPER_JAR" ]; then
    # Fallback to system gradle if wrapper jar isn't present
    if command -v gradle >/dev/null 2>&1; then
        exec gradle "$@"
    elif [ -x "/Users/k/.gradle/wrapper/dists/gradle-8.7-bin/bhs2wmbdwecv87pi65oeuq5iu/gradle-8.7/bin/gradle" ]; then
        exec "/Users/k/.gradle/wrapper/dists/gradle-8.7-bin/bhs2wmbdwecv87pi65oeuq5iu/gradle-8.7/bin/gradle" "$@"
    else
        echo "Error: Gradle executable not found." >&2
        exit 1
    fi
fi

exec "$JAVACMD" -jar "$WRAPPER_JAR" "$@"
