#!/bin/sh

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P) || exit 1
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ -n "$JAVA_HOME" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD=$(command -v java 2>/dev/null)
fi

if [ ! -x "$JAVACMD" ]; then
    echo "ERROR: Java was not found. Set JAVA_HOME to a JDK 17 or newer." >&2
    exit 1
fi

if [ ! -f "$CLASSPATH" ]; then
    echo "ERROR: gradle/wrapper/gradle-wrapper.jar is missing. Generate it from Android Studio or run 'gradle wrapper' once." >&2
    exit 1
fi

exec "$JAVACMD" -Dorg.gradle.appname=gradlew -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
