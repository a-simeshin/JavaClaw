@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM
@REM    http://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ----------------------------------------------------------------------------

@REM ----------------------------------------------------------------------------
@REM Apache Maven Wrapper startup batch script, version 3.3.2
@REM
@REM Optional ENV vars
@REM   JAVA_HOME - location of a JDK home dir, required when download maven via java source
@REM   MVNW_REPOURL - repo url base for downloading maven distribution
@REM   MVNW_USERNAME/MVNW_PASSWORD - user and password for downloading maven
@REM   MVNW_VERBOSE - true: enable verbose log; others: silence the output
@REM ----------------------------------------------------------------------------

@REM Begin all assignments locally
@ECHO OFF
SETLOCAL

SET "MVNW_REPOURL=%MVNW_REPOURL%"

@REM ==== START VALIDATION ====
IF NOT "%JAVA_HOME%"=="" GOTO OkJHome
FOR /F "tokens=* USEBACKQ" %%F IN (`where java 2^>NUL`) DO SET "JAVA_EXE=%%F"
IF NOT "%JAVA_EXE%"=="" GOTO OkJHome
ECHO Error: JAVA_HOME not found in your environment. >&2
ECHO Please set the JAVA_HOME variable in your environment to match the >&2
ECHO location of your Java installation. >&2
GOTO error
:OkJHome
IF NOT "%JAVA_HOME%"=="" SET "JAVA_EXE=%JAVA_HOME%\bin\java.exe"

@REM Determine the project base dir
SET "MAVEN_PROJECTBASEDIR=%~dp0"

@REM Find the maven-wrapper.properties
SET "WRAPPER_PROPERTIES=%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.properties"
IF NOT EXIST "%WRAPPER_PROPERTIES%" (
    ECHO Error: Could not find %WRAPPER_PROPERTIES% >&2
    GOTO error
)

@REM Read distributionUrl from maven-wrapper.properties
FOR /F "usebackq tokens=1,2 delims==" %%A IN ("%WRAPPER_PROPERTIES%") DO (
    IF "%%A"=="distributionUrl" SET "DISTRIBUTION_URL=%%B"
    IF "%%A"=="wrapperUrl" SET "WRAPPER_URL=%%B"
)

IF "%DISTRIBUTION_URL%"=="" SET "DISTRIBUTION_URL=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip"

@REM Determine Maven version from URL
SET "MVN_VERSION=3.9.9"

SET "MAVEN_USER_HOME=%USERPROFILE%\.m2"
SET "WRAPPER_CACHE_DIR=%MAVEN_USER_HOME%\wrapper\dists"
SET "MAVEN_HOME=%WRAPPER_CACHE_DIR%\apache-maven-%MVN_VERSION%"
SET "MVN_EXE=%MAVEN_HOME%\bin\mvn.cmd"

IF EXIST "%MVN_EXE%" GOTO execute

@REM Download maven
SET "WRAPPER_JAR=%MAVEN_PROJECTBASEDIR%.mvn\wrapper\maven-wrapper.jar"
IF EXIST "%WRAPPER_JAR%" (
    "%JAVA_EXE%" %MAVEN_OPTS% -jar "%WRAPPER_JAR%" "%DISTRIBUTION_URL%" "%MAVEN_HOME%" "%MAVEN_PROJECTBASEDIR%"
    IF ERRORLEVEL 1 GOTO error
    GOTO execute
)

@REM Download directly using PowerShell
IF NOT EXIST "%WRAPPER_CACHE_DIR%" MKDIR "%WRAPPER_CACHE_DIR%"
SET "ZIP_FILE=%WRAPPER_CACHE_DIR%\apache-maven-%MVN_VERSION%-bin.zip"

powershell -Command "& { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri '%DISTRIBUTION_URL%' -OutFile '%ZIP_FILE%' }"
IF ERRORLEVEL 1 (
    ECHO Error: Failed to download Maven distribution. >&2
    GOTO error
)

powershell -Command "& { Expand-Archive -Path '%ZIP_FILE%' -DestinationPath '%WRAPPER_CACHE_DIR%' -Force }"
IF ERRORLEVEL 1 (
    ECHO Error: Failed to unzip Maven distribution. >&2
    GOTO error
)
DEL /F /Q "%ZIP_FILE%"

:execute
SET "MAVEN_CMD_LINE_ARGS=%*"
"%MVN_EXE%" %MAVEN_OPTS% %MAVEN_CMD_LINE_ARGS%
IF ERRORLEVEL 1 GOTO error
GOTO end

:error
SET ERROR_CODE=1

:end
@ENDLOCAL & SET ERROR_CODE=%ERROR_CODE%
EXIT /B %ERROR_CODE%
