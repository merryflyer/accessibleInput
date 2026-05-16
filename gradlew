#!/bin/sh

##############################################################################
# Gradle wrapper shell script for macOS/Linux
##############################################################################

# 找到脚本所在目录（项目根目录）
APP_HOME="$( cd "$( dirname "$0" )" && pwd )"

WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
WRAPPER_PROPERTIES="$APP_HOME/gradle/wrapper/gradle-wrapper.properties"

# 确定 JAVA_HOME
if [ -z "$JAVA_HOME" ]; then
    JAVACMD=java
else
    JAVACMD="$JAVA_HOME/bin/java"
fi

# 启动 Gradle Wrapper
exec "$JAVACMD" \
    -classpath "$WRAPPER_JAR" \
    "-Dorg.gradle.appname=gradlew" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"
