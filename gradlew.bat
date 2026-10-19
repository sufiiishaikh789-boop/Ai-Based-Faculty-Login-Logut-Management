@echo off
rem Minimal Gradle wrapper launcher for Windows. Needs gradle\wrapper\gradle-wrapper.jar (binary; see README).
setlocal
set APP_HOME=%~dp0
set JAR=%APP_HOME%gradle\wrapper\gradle-wrapper.jar
if not exist "%JAR%" (
  echo ERROR: %JAR% is missing.
  echo Open the project in Android Studio ^(it downloads Gradle itself^), or run "gradle wrapper" once
  echo with a locally installed Gradle, or copy gradle-wrapper.jar from any other Android project.
  exit /b 1
)
if defined JAVA_HOME (set JAVACMD=%JAVA_HOME%\bin\java.exe) else (set JAVACMD=java.exe)
"%JAVACMD%" -Xmx64m -Xms64m "-Dorg.gradle.appname=gradlew" -jar "%JAR%" %*
endlocal
