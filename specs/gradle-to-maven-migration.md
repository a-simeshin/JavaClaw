# Plan: Миграция Gradle → Maven

## Task Description

Полная миграция системы сборки проекта JavaClaw с Gradle на Maven. Текущий проект — мультимодульный Spring Boot 4.0.3 + Spring AI 2.0.0-SNAPSHOT с 10 модулями. Эталонная конфигурация плагинов Maven берётся из aihub-проектов `adapters-java-sdk-parent` и `cm-otlp-javaagent`.

## Objective

После выполнения плана проект JavaClaw будет полностью собираться через Maven (`./mvnw verify`), включая все модули, тесты, форматирование кода (Spotless), покрытие (JaCoCo), статический анализ (PMD, SpotBugs), и Docker-образы (`spring-boot:build-image`). Все Gradle-файлы будут удалены.

## Problem Statement

Текущая сборка на Gradle не соответствует стандартам enterprise-проектов aihub (где все сервисы используют Maven). Необходимо унифицировать систему сборки для интеграции с CI/CD, SonarQube, и общими Maven BOM-ами экосистемы aihub.

## Solution Approach

1. Создать корневой `pom.xml` с `spring-boot-starter-parent` 4.0.3, централизованным `<dependencyManagement>` и `<pluginManagement>` по образцу `adapters-java-sdk-parent`
2. Плоская структура модулей (`<module>plugins/discord</module>` и т.д.) без промежуточных parent POM
3. Перенести все зависимости из `build.gradle` в соответствующие `pom.xml`
4. Добавить все плагины качества: Spotless (palantirJavaFormat), JaCoCo, PMD, SpotBugs, Maven Surefire
5. Заменить Jib на `spring-boot-maven-plugin` с `build-image` goal
6. Установить Maven Wrapper, удалить Gradle-файлы

## Relevant Files

### Эталонные конфигурации (только для чтения)

- `/Users/artemsimeisn/IdeaProjects/sber/aihub/adapters-java-sdk/adapters-java-sdk-parent/pom.xml` — основной эталон: pluginManagement, dependencyManagement, Spotless, JaCoCo
- `/Users/artemsimeisn/IdeaProjects/sber/aihub/adapters-java-services/pom.xml` — эталон мультимодульного сервиса
- `/Users/artemsimeisn/IdeaProjects/sber/aihub/cm-otlp-javaagent/pom.xml` — эталон PMD, SpotBugs, строгие coverage rules

### Текущие Gradle-файлы (будут удалены)

- `build.gradle` — корневой: Spring Boot 4.0.3, Spring AI BOM 2.0.0-SNAPSHOT, Modulith BOM 2.0.3, Java 21
- `settings.gradle` — список модулей
- `base/build.gradle` — core зависимости: spring-ai, spring-data-jdbc, jobrunr, modulith, mcp-client, testcontainers
- `app/build.gradle` — Spring Boot app: все модули + actuator, webmvc, websocket, pebble, flyway, jib, playwright tests
- `plugins/discord/build.gradle` — JDA:6.1.1
- `plugins/telegram/build.gradle` — telegrambots:9.4.0
- `plugins/playwright/build.gradle` — playwright + spring-ai-client-chat
- `plugins/brave/build.gradle` — spring-ai-agent-utils
- `providers/anthropic/build.gradle` — spring-ai-starter-model-anthropic
- `providers/google/build.gradle` — spring-ai-starter-model-google-genai
- `providers/ollama/build.gradle` — spring-ai-starter-model-ollama
- `providers/openai/build.gradle` — spring-ai-starter-model-openai

### New Files

- `pom.xml` — корневой Maven POM (parent + module aggregator)
- `base/pom.xml`
- `app/pom.xml`
- `plugins/discord/pom.xml`
- `plugins/telegram/pom.xml`
- `plugins/playwright/pom.xml`
- `plugins/brave/pom.xml`
- `providers/anthropic/pom.xml`
- `providers/google/pom.xml`
- `providers/ollama/pom.xml`
- `providers/openai/pom.xml`
- `.mvn/wrapper/maven-wrapper.properties`

### Файлы для обновления

- `.gitignore` — заменить Gradle-паттерны на Maven (target/, *.iml и т.д.)

## Implementation Phases

### Phase 1: Foundation

Создание корневого `pom.xml` с полной конфигурацией `<properties>`, `<dependencyManagement>`, `<pluginManagement>`. Установка Maven Wrapper.

### Phase 2: Core Implementation

Создание `pom.xml` для каждого из 10 модулей с точным переносом зависимостей из Gradle. Порядок: base → providers → plugins → app.

### Phase 3: Integration & Polish

Валидация компиляции и тестов. Удаление всех Gradle-файлов. Обновление `.gitignore` и документации.

## Team Orchestration

- You operate as the team lead and orchestrate the team to execute the plan.
- You're responsible for deploying the right team members with the right context to execute the plan.
- IMPORTANT: You NEVER operate directly on the codebase. You use `Task` and `Task*` tools to deploy team members to to the building, validating, testing, deploying, and other tasks.
  - This is critical. You're job is to act as a high level director of the team, not a builder.
  - You're role is to validate all work is going well and make sure the team is on track to complete the plan.
  - You'll orchestrate this by using the Task* Tools to manage coordination between the team members.
  - Communication is paramount. You'll use the Task* Tools to communicate with the team members and ensure they're on track to complete the plan.
- Take note of the session id of each team member. This is how you'll reference them.

### Team Members

- Builder
  - Name: builder-maven-root
  - Role: Создание корневого pom.xml и Maven Wrapper
  - Agent Type: builder
  - Resume: true
- Builder
  - Name: builder-maven-modules
  - Role: Создание pom.xml для всех 10 дочерних модулей
  - Agent Type: builder
  - Resume: true
- Builder
  - Name: builder-cleanup
  - Role: Удаление Gradle-файлов, обновление .gitignore
  - Agent Type: builder
  - Resume: true
- Builder
  - Name: builder-tests
  - Role: Запуск и починка тестов после миграции
  - Agent Type: builder
  - Resume: true
- Validator
  - Name: validator-final
  - Role: Финальная валидация: компиляция, тесты, плагины качества
  - Agent Type: validator
  - Resume: false

## Testing Strategy

Test pyramid ratio: **80% unit / 15% integration-API / 5% UI e2e**

### Unit Tests (80%)

- Существующие unit-тесты из `app/src/test/java/ai/javaclaw/chat/`, `plugins/discord/src/test/`, `plugins/playwright/src/test/`, `providers/anthropic/src/test/` — должны пройти без изменений после миграции
- `ChatChannelTest`, `ChatWebSocketHandlerTest`, `DiscordOnboardingProviderTest`, `DiscordChannelTest`, `PlaywrightAutoConfigurationTests`, `PlaywrightOnboardingProviderTest`, `AnthropicClaudeCodeBackendTest`

### Integration / API Tests (15%)

- `OnboardingControllerTest` — Spring MVC тесты
- `ChatMemoryLiveTest`, `AgentLiveTest`, `TaskLiveTest` — интеграция с Testcontainers PostgreSQL
- Все тесты из `base/src/test/` — JDBC + Testcontainers

### UI E2E Tests (5%)

- `ChatE2ETest`, `ConversationSwitchingE2ETest`, `TaskCreationE2ETest`, `MultiInstanceE2ETest`, `OnboardingE2ETest` — Playwright E2E тесты
- Критично: тесты требуют `forkEvery=0` / `maxParallelForks=1` (в Maven: `<forkCount>1</forkCount>`, `<reuseForks>true</reuseForks>`)

## Step by Step Tasks

- IMPORTANT: Execute every step in order, top to bottom. Each task maps directly to a `TaskCreate` call.
- Before you start, run `TaskCreate` to create the initial task list that all team members can see and execute.

### 1. Создать корневой pom.xml

- **Task ID**: create-root-pom
- **Depends On**: none
- **Assigned To**: builder-maven-root
- **Agent Type**: builder
- **Stack**: Java Spring Boot maven lombok spring-ai
- **Parallel**: false
- **Tests**: Нет (структурная задача)
- Создать `pom.xml` в корне проекта со следующей структурой:
  - `<parent>`: `org.springframework.boot:spring-boot-starter-parent:4.0.3`
  - `<groupId>`: `ai.javaclaw`
  - `<artifactId>`: `javaclaw-parent`
  - `<version>`: `1.0.0-SNAPSHOT`
  - `<packaging>`: `pom`
  - `<modules>`: base, plugins/discord, plugins/telegram, plugins/playwright, plugins/brave, providers/anthropic, providers/google, providers/ollama, providers/openai, app
  - `<properties>`:

    ```
    java.version=21
    maven.compiler.release=21
    project.build.sourceEncoding=UTF-8
    spring-boot.version=4.0.3
    spring-ai.version=2.0.0-SNAPSHOT
    spring-modulith.version=2.0.3
    spring-ai-agent-utils.version=0.6.0-SNAPSHOT
    jobrunr.version=8.5.1
    jda.version=6.1.1
    telegrambots.version=9.4.0
    playwright.version=1.52.0
    pebble.version=4.1.1
    netty-macos.version=4.2.10.Final
    spotless-maven-plugin.version=2.45.0
    jacoco.version=0.8.13
    maven-compiler-plugin.version=3.14.1
    maven-pmd-plugin.version=3.26.0
    spotbugs-maven-plugin.version=4.9.3.0
    ```
  - `<repositories>`: mavenCentral + repo.spring.io/snapshot + repo.spring.io/milestone + central.sonatype.com/repository/maven-snapshots
  - `<dependencyManagement>` (НЕ импортировать `spring-boot-dependencies` BOM — он уже наследуется от `spring-boot-starter-parent`):
    - `spring-ai-bom:2.0.0-SNAPSHOT` (import)
    - `spring-modulith-bom:2.0.3` (import)
    - `org.springaicommunity:spring-ai-agent-utils:${spring-ai-agent-utils.version}`
    - `org.jobrunr:jobrunr-spring-boot-4-starter:${jobrunr.version}`
    - `net.dv8tion:JDA:${jda.version}`
    - `org.telegram:telegrambots-springboot-longpolling-starter:${telegrambots.version}`
    - `org.telegram:telegrambots-client:${telegrambots.version}`
    - `com.microsoft.playwright:playwright:${playwright.version}`
    - `io.pebbletemplates:pebble-spring-boot-starter:${pebble.version}`
    - `io.netty:netty-resolver-dns-native-macos:${netty-macos.version}`
  - `<pluginManagement>` (по образцу `adapters-java-sdk-parent` + `cm-otlp-javaagent`):
    - **maven-compiler-plugin** v3.14.1: `<release>21</release>`, `-parameters` compilerArg
    - **spring-boot-maven-plugin** v4.0.3
    - **spotless-maven-plugin** v2.45.0: palantirJavaFormat для Java, flexmark для Markdown; executions: apply (process-resources), check (verify)
    - **maven-surefire-plugin**: `<reuseForks>true</reuseForks>`, `<forkCount>1</forkCount>`, `<argLine>${surefireArgLine}</argLine>`
    - **jacoco-maven-plugin** v0.8.13: prepare-agent (destFile=target/coverage-reports/jacoco.exec, propertyName=surefireArgLine), report (phase=test)
    - **maven-pmd-plugin** v3.26.0: `<rulesets><ruleset>/category/java/bestpractices.xml</ruleset></rulesets>`, `<failOnViolation>true</failOnViolation>`
    - **spotbugs-maven-plugin** v4.9.3.0: `<effort>Max</effort>`, `<threshold>Medium</threshold>`, `<failOnError>true</failOnError>`
  - `<plugins>` (activate from pluginManagement): maven-compiler-plugin, spotless-maven-plugin, maven-surefire-plugin, jacoco-maven-plugin, maven-pmd-plugin, spotbugs-maven-plugin

### 2. Установить Maven Wrapper

- **Task ID**: setup-maven-wrapper
- **Depends On**: create-root-pom
- **Assigned To**: builder-maven-root
- **Agent Type**: builder
- **Stack**: Java maven
- **Parallel**: false
- **Tests**: `./mvnw --version` должен работать
- Выполнить `mvn wrapper:wrapper -Dmaven=3.9.9` (или скачать wrapper вручную)
- Создать `.mvn/wrapper/maven-wrapper.properties` с `distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip`
- Убедиться что `mvnw` и `mvnw.cmd` созданы и executable

### 3. Создать pom.xml для модуля base

- **Task ID**: create-base-pom
- **Depends On**: create-root-pom
- **Assigned To**: builder-maven-modules
- **Agent Type**: builder
- **Stack**: Java Spring Boot maven spring-ai spring-data-jdbc
- **Parallel**: true (с задачами 4, 5)
- **Tests**: `./mvnw compile -pl base` проходит
- Создать `base/pom.xml`:
  - `<parent>`: javaclaw-parent
  - `<artifactId>`: javaclaw-base
  - Зависимости (из `base/build.gradle`):
    - `spring-boot-starter` (implementation)
    - `spring-modulith-starter-core` (implementation)
    - `jobrunr-spring-boot-4-starter` (implementation, version из parent)
    - `commons-lang3` (implementation)
    - `netty-resolver-dns-native-macos` (runtime)
    - `spring-ai-client-chat` (implementation)
    - `spring-ai-starter-model-chat-memory-repository-jdbc` (implementation)
    - `spring-ai-starter-mcp-client` (implementation)
    - `spring-ai-agent-utils` (implementation, version из parent)
    - `spring-boot-starter-data-jdbc` (implementation)
    - `spring-boot-starter-test` (test)
    - `spring-boot-starter-data-jdbc-test` (test)
    - `spring-boot-testcontainers` (test)
    - `awaitility` (test)
    - `testcontainers-junit-jupiter` (test)
    - `testcontainers-postgresql` (test)
    - `spring-boot-flyway` (test)
    - `flyway-database-postgresql` (test)
    - `postgresql` (test, runtime)
    - `junit-platform-launcher` (test, runtime)

### 4. Создать pom.xml для всех provider-модулей

- **Task ID**: create-providers-poms
- **Depends On**: create-root-pom
- **Assigned To**: builder-maven-modules
- **Agent Type**: builder
- **Stack**: Java Spring Boot maven spring-ai
- **Parallel**: true (с задачами 3, 5)
- **Tests**: `./mvnw compile -pl providers/anthropic,providers/google,providers/ollama,providers/openai` проходит
- Создать 4 файла `providers/{name}/pom.xml` по единому шаблону:
  - `<parent>`: javaclaw-parent
  - `<artifactId>`: javaclaw-provider-{name}
  - Общие зависимости для каждого:
    - `javaclaw-base` (implementation)
    - `spring-boot-starter` (implementation)
    - `spring-boot-starter-test` (test)
    - `junit-platform-launcher` (test, runtime)
  - Уникальная зависимость:
    - **anthropic**: `spring-ai-starter-model-anthropic`
    - **google**: `spring-ai-starter-model-google-genai`
    - **ollama**: `spring-ai-starter-model-ollama`
    - **openai**: `spring-ai-starter-model-openai`

### 5. Создать pom.xml для всех plugin-модулей

- **Task ID**: create-plugins-poms
- **Depends On**: create-root-pom
- **Assigned To**: builder-maven-modules
- **Agent Type**: builder
- **Stack**: Java Spring Boot maven
- **Parallel**: true (с задачами 3, 4)
- **Tests**: `./mvnw compile -pl plugins/discord,plugins/telegram,plugins/playwright,plugins/brave` проходит
- Создать 4 файла `plugins/{name}/pom.xml`:
  - `<parent>`: javaclaw-parent
  - Общие зависимости для каждого:
    - `javaclaw-base` (implementation)
    - `spring-boot-starter` (implementation)
    - `spring-boot-starter-test` (test)
    - `junit-platform-launcher` (test, runtime)
  - Уникальные зависимости:
    - **discord** (`javaclaw-plugin-discord`): `net.dv8tion:JDA` (version из parent)
    - **telegram** (`javaclaw-plugin-telegram`): `telegrambots-springboot-longpolling-starter`, `telegrambots-client` (versions из parent)
    - **playwright** (`javaclaw-plugin-playwright`): `com.microsoft.playwright:playwright` (version из parent), `org.springframework.ai:spring-ai-client-chat` (compile)
    - **brave** (`javaclaw-plugin-brave`): `spring-ai-agent-utils` (version из parent). Test: `spring-boot-restclient-test`

### 6. Создать pom.xml для модуля app

- **Task ID**: create-app-pom
- **Depends On**: create-base-pom, create-providers-poms, create-plugins-poms
- **Assigned To**: builder-maven-modules
- **Agent Type**: builder
- **Stack**: Java Spring Boot maven spring-data-jdbc controller
- **Parallel**: false
- **Tests**: `./mvnw compile -pl app` проходит
- Создать `app/pom.xml`:
  - `<parent>`: javaclaw-parent
  - `<artifactId>`: javaclaw-app
  - Зависимости модулей:
    - `javaclaw-base`, `javaclaw-provider-anthropic`, `javaclaw-provider-google`, `javaclaw-provider-ollama`, `javaclaw-provider-openai`
    - `javaclaw-plugin-discord`, `javaclaw-plugin-telegram`, `javaclaw-plugin-playwright`, `javaclaw-plugin-brave`
  - Внешние зависимости:
    - `spring-ai-client-chat`
    - `spring-boot-starter-actuator`
    - `spring-boot-starter-data-jdbc`
    - `spring-boot-starter-restclient`
    - `spring-boot-starter-webmvc`
    - `spring-boot-starter-websocket`
    - `pebble-spring-boot-starter` (version из parent)
    - `spring-boot-devtools` (scope: runtime, optional: true)
    - `postgresql` (scope: runtime)
    - `spring-boot-flyway`
    - `flyway-database-postgresql`
  - Test зависимости:
    - `spring-boot-starter-test`
    - `spring-boot-starter-data-jdbc-test`
    - `spring-boot-starter-restclient-test`
    - `spring-boot-starter-webmvc-test`
    - `spring-boot-starter-websocket-test`
    - `spring-boot-testcontainers`
    - `testcontainers-junit-jupiter`
    - `testcontainers-postgresql`
    - `awaitility`
    - `playwright` (version из parent)
    - `junit-platform-launcher` (test, runtime)
  - Plugins:
    - `spring-boot-maven-plugin` с `repackage` goal (для fat JAR) и `build-image` goal
    - Конфигурация build-image с включением workspace через bindings:

      ```xml
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
        <configuration>
          <image>
            <name>jobrunr.io/javaclaw:latest</name>
            <bindings>
              <binding>${project.basedir}/../workspace:/workspace:ro</binding>
            </bindings>
          </image>
        </configuration>
      </plugin>
      ```
    - ВАЖНО: Jib копировал `workspace/` в `/workspace` внутри образа. В `build-image` (Cloud Native Buildpacks) используется `<bindings>` для монтирования. Если bindings не подходят — альтернатива: использовать `docker-compose.dev.yml` для volume mount или добавить Dockerfile с `COPY workspace /workspace`
  - Maven Surefire override для app:

    ```xml
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-surefire-plugin</artifactId>
      <configuration>
        <forkCount>1</forkCount>
        <reuseForks>true</reuseForks>
        <argLine>
          -XX:+TieredCompilation -XX:TieredStopAtLevel=1
          ${surefireArgLine}
        </argLine>
        <environmentVariables>
          <OPENROUTER_API_KEY>${env.OPENROUTER_API_KEY}</OPENROUTER_API_KEY>
        </environmentVariables>
      </configuration>
    </plugin>
    ```

### 7. Обновить .gitignore и удалить Gradle-файлы

- **Task ID**: cleanup-gradle
- **Depends On**: create-app-pom
- **Assigned To**: builder-cleanup
- **Agent Type**: builder
- **Stack**: Java maven
- **Parallel**: false
- **Tests**: `ls build.gradle` → файл не найден; `ls pom.xml` → файл найден
- Удалить файлы:
  - `build.gradle` (корневой)
  - `settings.gradle`
  - `gradlew`, `gradlew.bat`
  - `gradle/` (вся директория)
  - `base/build.gradle`
  - `app/build.gradle`
  - `plugins/build.gradle`
  - `plugins/discord/build.gradle`
  - `plugins/telegram/build.gradle`
  - `plugins/playwright/build.gradle`
  - `plugins/brave/build.gradle`
  - `providers/anthropic/build.gradle`
  - `providers/google/build.gradle`
  - `providers/ollama/build.gradle`
  - `providers/openai/build.gradle`
- Обновить `.gitignore`:
  - Удалить Gradle-паттерны (`.gradle/`, `build/`)
  - Добавить Maven-паттерны: `target/`, `*.iml`, `.mvn/wrapper/maven-wrapper.jar`
- Удалить пустой `plugins/build.gradle` (содержит только `plugins { id 'base' }`)

### 8. Компиляция и починка

- **Task ID**: fix-compilation
- **Depends On**: cleanup-gradle
- **Assigned To**: builder-tests
- **Agent Type**: builder
- **Stack**: Java Spring Boot maven spring-ai exception error handling
- **Parallel**: false
- **Tests**: `./mvnw compile` проходит без ошибок
- Выполнить `./mvnw compile` и исправить все ошибки компиляции
- Типичные проблемы: неверные groupId/artifactId, отсутствующие версии, проблемы с BOM-ами Spring Boot 4.x
- Убедиться что все модули компилируются

### 9. Запуск тестов и починка

- **Task ID**: fix-tests
- **Depends On**: fix-compilation
- **Assigned To**: builder-tests
- **Agent Type**: builder
- **Stack**: Java Spring Boot maven MockMvc Mockito assertj test structure testcontainers integration test surefire failsafe jacoco
- **Parallel**: false
- **Tests**: `./mvnw test` проходит; Unit: все существующие тесты. Integration: Testcontainers PostgreSQL тесты.
- Выполнить `./mvnw test` и исправить все сбои
- Проверить что JaCoCo генерирует отчёт в `target/site/jacoco/jacoco.xml`
- Проверить что Spotless не ломает форматирование (или пропустить: `./mvnw spotless:apply`)

### 10. Запуск плагинов качества и починка

- **Task ID**: fix-quality-plugins
- **Depends On**: fix-tests
- **Assigned To**: builder-tests
- **Agent Type**: builder
- **Stack**: Java maven surefire failsafe jacoco
- **Parallel**: false
- **Tests**: `./mvnw verify` проходит без ошибок
- Выполнить `./mvnw verify` — это запускает Spotless check, PMD, SpotBugs, JaCoCo
- Исправить нарушения Spotless: `./mvnw spotless:apply`
- Исправить PMD violations или добавить exclusions при необходимости
- Исправить SpotBugs findings или добавить `@SuppressFBWarnings` при необходимости
- Если плагины слишком строгие для текущего кода — ослабить правила в корневом pom.xml (но не отключать полностью)

### 11. Финальная валидация

- **Task ID**: validate-all
- **Depends On**: fix-quality-plugins
- **Assigned To**: validator-final
- **Agent Type**: validator
- **Stack**: Java Spring Boot maven surefire failsafe jacoco MockMvc testcontainers integration test
- **Parallel**: false
- Выполнить полный цикл валидации:
  1. `./mvnw clean verify` — должен пройти без ошибок
  2. Проверить что все 10 модулей собраны (10 `target/` директорий)
  3. Проверить что JaCoCo отчёт существует в `app/target/site/jacoco/`
  4. Проверить что `app/target/javaclaw-app.jar` (или `.jar`) собран как Spring Boot fat jar
  5. Проверить что Gradle-файлы удалены (ни одного `build.gradle`, `settings.gradle`, `gradlew`)
  6. Проверить что Maven Wrapper работает: `./mvnw --version`
  7. Проверить что `.gitignore` содержит `target/` и не содержит `build/`

## Acceptance Criteria

- [ ] `./mvnw clean verify` проходит успешно на всех 10 модулях
- [ ] Все существующие тесты проходят (unit, integration, e2e)
- [ ] JaCoCo генерирует отчёт покрытия
- [ ] Spotless check проходит (код отформатирован)
- [ ] PMD и SpotBugs не находят критических нарушений (или exclusions задокументированы)
- [ ] Spring Boot fat jar собирается для модуля app
- [ ] `spring-boot:build-image` работает (Docker-образ создаётся)
- [ ] Все Gradle-файлы удалены (`build.gradle`, `settings.gradle`, `gradle/`, `gradlew*`)
- [ ] Maven Wrapper (`./mvnw`) установлен и работает
- [ ] `.gitignore` обновлён для Maven

## Validation Commands

Execute these commands to validate the task is complete:

- `./mvnw --version` — Maven Wrapper работает
- `./mvnw clean compile` — все модули компилируются
- `./mvnw test` — все тесты проходят
- `./mvnw verify` — Spotless, JaCoCo, PMD, SpotBugs проходят
- `ls build.gradle 2>/dev/null && echo "FAIL: Gradle files exist" || echo "OK: No Gradle files"` — Gradle удалён
- `ls app/target/javaclaw-app.jar && echo "OK: Fat JAR exists"` — Spring Boot JAR собран
- `ls app/target/site/jacoco/jacoco.xml && echo "OK: JaCoCo report exists"` — JaCoCo работает

## Notes

- Spring Boot 4.0.3 — свежая версия, некоторые артефакты могут быть только в snapshot/milestone репозиториях Spring
- Spring AI 2.0.0-SNAPSHOT — требует `repo.spring.io/snapshot` repository
- `org.springaicommunity:spring-ai-agent-utils:0.6.0-SNAPSHOT` — требует `central.sonatype.com/repository/maven-snapshots` repository
- Модуль `providers` не имеет собственного build.gradle в Gradle-проекте — в Maven просто не создаём для него pom.xml
- `workspace/` директория используется в runtime (копируется в Docker через jib). В `build-image` (Buildpacks) используются `<bindings>` для монтирования. Если это не подходит для production — рассмотреть Dockerfile + `docker-compose.dev.yml` volume mount как альтернативу
- `plugins/build.gradle` содержит только `plugins { id 'base' }` — это Gradle-специфичный плагин для пустого агрегирующего модуля, в Maven не нужен

