#!/bin/sh
set -e
if [ -d "work/tools/temurin-21/jdk-21.0.11+10" ]; then
  export JAVA_HOME="$(pwd)/work/tools/temurin-21/jdk-21.0.11+10"
  export PATH="$JAVA_HOME/bin:$PATH"
fi
if [ -x "work/tools/gradle-8.14.3/bin/gradle" ]; then
  exec "work/tools/gradle-8.14.3/bin/gradle" "$@"
fi
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi
echo "Gradle is not installed and no Gradle Wrapper JAR is bundled. Install Gradle or run 'gradle wrapper' once." >&2
exit 1
