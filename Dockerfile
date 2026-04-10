# ---- Stage 1: Build frontend ----
FROM node:22-alpine AS frontend
RUN corepack enable && corepack prepare pnpm@latest --activate
WORKDIR /app/javaclaw-frontend
COPY javaclaw-frontend/package.json javaclaw-frontend/pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY javaclaw-frontend/ ./
RUN pnpm build

# ---- Stage 2: Build backend ----
FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /app
COPY pom.xml ./
COPY javaclaw-core/pom.xml javaclaw-core/pom.xml
COPY javaclaw-api/pom.xml javaclaw-api/pom.xml
COPY javaclaw-api/javaclaw-api-chat/pom.xml javaclaw-api/javaclaw-api-chat/pom.xml
COPY javaclaw-api/javaclaw-api-admin/pom.xml javaclaw-api/javaclaw-api-admin/pom.xml
COPY javaclaw-app/pom.xml javaclaw-app/pom.xml
COPY javaclaw-frontend/pom.xml javaclaw-frontend/pom.xml
COPY javaclaw-channel/pom.xml javaclaw-channel/pom.xml
COPY javaclaw-channel/javaclaw-channel-discord/pom.xml javaclaw-channel/javaclaw-channel-discord/pom.xml
COPY javaclaw-channel/javaclaw-channel-telegram/pom.xml javaclaw-channel/javaclaw-channel-telegram/pom.xml
COPY javaclaw-provider/pom.xml javaclaw-provider/pom.xml
COPY javaclaw-provider/javaclaw-provider-anthropic/pom.xml javaclaw-provider/javaclaw-provider-anthropic/pom.xml
COPY javaclaw-provider/javaclaw-provider-google/pom.xml javaclaw-provider/javaclaw-provider-google/pom.xml
COPY javaclaw-provider/javaclaw-provider-ollama/pom.xml javaclaw-provider/javaclaw-provider-ollama/pom.xml
COPY javaclaw-provider/javaclaw-provider-openai/pom.xml javaclaw-provider/javaclaw-provider-openai/pom.xml
COPY javaclaw-e2e/pom.xml javaclaw-e2e/pom.xml
# Download dependencies first (cached layer)
RUN mvn dependency:go-offline -B -q 2>/dev/null || true

# Copy source
COPY javaclaw-core/ javaclaw-core/
COPY javaclaw-api/ javaclaw-api/
COPY javaclaw-app/ javaclaw-app/
COPY javaclaw-channel/ javaclaw-channel/
COPY javaclaw-provider/ javaclaw-provider/
COPY javaclaw-frontend/pom.xml javaclaw-frontend/pom.xml

# Copy frontend dist into the location maven-resources-plugin expects
COPY --from=frontend /app/javaclaw-frontend/dist javaclaw-frontend/dist/

# Build (skip tests — they need a DB)
RUN mvn package -pl javaclaw-app -am -DskipTests -B -q

# ---- Stage 3: Runtime ----
FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -g 1001 javaclaw && adduser -u 1001 -G javaclaw -D javaclaw
WORKDIR /app

COPY --from=backend /app/javaclaw-app/target/javaclaw-app-*-exec.jar app.jar

USER javaclaw

EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
