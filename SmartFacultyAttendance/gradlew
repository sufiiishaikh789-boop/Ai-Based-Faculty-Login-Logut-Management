#!/bin/sh
# Minimal Gradle wrapper launcher. It needs gradle/wrapper/gradle-wrapper.jar, which is a binary file
# that could not be generated in the offline authoring environment. See README "Gradle wrapper".
APP_HOME=$(cd "$(dirname "$0")" && pwd -P)
JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$JAR" ]; then
  echo "ERROR: $JAR is missing." >&2
  echo "Open the project in Android Studio (it downloads Gradle itself), or run 'gradle wrapper' once" >&2
  echo "with a locally installed Gradle, or copy gradle-wrapper.jar from any other Android project." >&2
  exit 1
fi
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then JAVACMD="$JAVA_HOME/bin/java"; else JAVACMD=java; fi
exec "$JAVACMD" -Xmx64m -Xms64m "-Dorg.gradle.appname=gradlew" -jar "$JAR" "$@"
