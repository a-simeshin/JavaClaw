#!/bin/sh
# ----------------------------------------------------------------------------
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
# ----------------------------------------------------------------------------

# ----------------------------------------------------------------------------
# Apache Maven Wrapper startup batch script, version 3.3.2
#
# Optional ENV vars
# -----------------
#   JAVA_HOME - location of a JDK home dir, required when download maven via java source
#   MVNW_REPOURL - repo url base for downloading maven distribution
#   MVNW_USERNAME/MVNW_PASSWORD - user and password for downloading maven
#   MVNW_VERBOSE - true: enable verbose log; debug: trace the mvnw script; others: silence the output
# ----------------------------------------------------------------------------

set -euf
[ "${MVNW_VERBOSE-}" != debug ] || set -x

# OS specific support.
native_path() { printf %s\\n "$1"; }
case "$(uname)" in
CYGWIN* | MINGW*)
  [ -z "${JAVA_HOME-}" ] || JAVA_HOME="$(cygpath --unix "$JAVA_HOME")"
  native_path() { cygpath --path --windows "$1"; }
  ;;
esac

# set JAVA_HOME if not already set
if [ -z "${JAVA_HOME-}" ]; then
  if [ -x /usr/libexec/java_home ]; then
    JAVA_HOME="$(/usr/libexec/java_home)" || :
  fi
fi

# Determine MAVEN_HOME from the maven-wrapper.properties if not already set
if [ -z "${MAVEN_HOME-}" ]; then
  # shellcheck disable=SC2039,SC3028
  case ${BASH_SOURCE-} in */*) script_dir=${BASH_SOURCE%/*};; *) script_dir=.;; esac
  [ -f "$script_dir/.mvn/wrapper/maven-wrapper.properties" ] || script_dir="$(cd -- "$(dirname -- "$0")" && pwd -P)"

  distributionUrl="$(sed -n 's/^distributionUrl=//p' "$script_dir/.mvn/wrapper/maven-wrapper.properties" | tr -d \\r)"
  distributionUrl="${distributionUrl:-https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip}"
  wrapperUrl="$(sed -n 's/^wrapperUrl=//p' "$script_dir/.mvn/wrapper/maven-wrapper.properties" | tr -d \\r)"
  wrapperUrl="${wrapperUrl:-https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar}"

  wrapperJarPath="$script_dir/.mvn/wrapper/maven-wrapper.jar"
  wrapperMainClass="org.apache.maven.wrapper.MavenWrapperMain"

  MAVEN_USER_HOME="${MAVEN_USER_HOME:-${HOME}/.m2}"
  WRAPPER_CACHE_DIR="$MAVEN_USER_HOME/wrapper/dists"

  # Determine maven version from distribution URL
  case "$distributionUrl" in
    */apache-maven-*-bin.zip)
      mvnVersion="${distributionUrl##*/apache-maven-}"
      mvnVersion="${mvnVersion%-bin.zip}"
      ;;
    *)
      mvnVersion="unknown"
      ;;
  esac

  MAVEN_HOME="$WRAPPER_CACHE_DIR/apache-maven-$mvnVersion"
fi

mvnExe="$MAVEN_HOME/bin/mvn"

if [ ! -x "$mvnExe" ]; then
  # Download maven distribution
  mkdir -p "$WRAPPER_CACHE_DIR"

  if [ -r "$wrapperJarPath" ]; then
    # Use the wrapper jar to download maven
    if [ -n "${JAVA_HOME-}" ]; then
      javaExe="$JAVA_HOME/bin/java"
    else
      javaExe=java
    fi
    [ "${MVNW_VERBOSE-}" != true ] || echo "Found wrapper jar; using $javaExe to download Maven"
    MVNW_USERNAME="${MVNW_USERNAME-}" MVNW_PASSWORD="${MVNW_PASSWORD-}" \
      "$javaExe" \
        $MAVEN_OPTS \
        ${MVNW_USERNAME:+-Dmaven.repo.remote.username="$MVNW_USERNAME"} \
        ${MVNW_PASSWORD:+-Dmaven.repo.remote.password="$MVNW_PASSWORD"} \
        -jar "$wrapperJarPath" "$distributionUrl" "$MAVEN_HOME" "$script_dir" \
      || rm -rf "$MAVEN_HOME"
  else
    # Download directly
    if command -v wget > /dev/null; then
      dlCmd=wget
      if [ "${MVNW_VERBOSE-}" = true ]; then
        dlFlags="-v"
      else
        dlFlags="-q"
      fi
      [ -z "${MVNW_USERNAME-}" ] || dlFlags="$dlFlags --http-user=$MVNW_USERNAME --http-password=$MVNW_PASSWORD"
      [ -z "${MVNW_PASSWORD-}" ] || dlFlags="$dlFlags --http-password=$MVNW_PASSWORD"
      dlFlags="$dlFlags -O"
    elif command -v curl > /dev/null; then
      dlCmd=curl
      if [ "${MVNW_VERBOSE-}" = true ]; then
        dlFlags="-v"
      else
        dlFlags="-sS"
      fi
      [ -z "${MVNW_USERNAME-}" ] || dlFlags="$dlFlags -u $MVNW_USERNAME:$MVNW_PASSWORD"
      dlFlags="$dlFlags -o"
    else
      echo "Error: wget or curl is required to download Maven." >&2
      exit 1
    fi

    [ "${MVNW_VERBOSE-}" != true ] || echo "Downloading Maven from $distributionUrl"

    zipFile="$WRAPPER_CACHE_DIR/apache-maven-$mvnVersion-bin.zip"
    $dlCmd $dlFlags "$zipFile" "$distributionUrl" || {
      echo "Error: Failed to download Maven distribution." >&2
      rm -f "$zipFile"
      exit 1
    }

    # Unzip
    [ "${MVNW_VERBOSE-}" != true ] || echo "Unzipping Maven to $MAVEN_HOME"
    unzip -q -o -d "$WRAPPER_CACHE_DIR" "$zipFile" || {
      echo "Error: Failed to unzip Maven distribution." >&2
      rm -f "$zipFile"
      exit 1
    }
    rm -f "$zipFile"
  fi

  if [ ! -x "$mvnExe" ]; then
    echo "Error: Maven executable not found at $mvnExe" >&2
    exit 1
  fi
fi

# Provide a "standardized" way to retrieve the CLI args that will
# work with both Windows and non-Windows executions.
MAVEN_CMD_LINE_ARGS="$*"
export MAVEN_CMD_LINE_ARGS

exec "$mvnExe" \
  ${MAVEN_PROJECTBASEDIR:+-Dmaven.multiModuleProjectDirectory="$MAVEN_PROJECTBASEDIR"} \
  "$@"
