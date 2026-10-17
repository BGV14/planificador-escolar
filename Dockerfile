# syntax=docker/dockerfile:1
FROM node:22-alpine AS frontend
WORKDIR /build/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/index.html frontend/tsconfig.json frontend/vite.config.ts ./
COPY frontend/src ./src
RUN npm run build

FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /build/backend
COPY backend/pom.xml ./
RUN mvn -B -ntp dependency:go-offline
COPY backend/src/main ./src/main
# Generated assets exist only inside the image build, never in the source tree.
COPY --from=frontend /build/frontend/dist ./src/main/resources/static
RUN mvn -B -ntp -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app
RUN groupadd --gid 10001 planner && useradd --uid 10001 --gid planner --no-create-home planner
COPY --from=backend --chown=10001:10001 /build/backend/target/planner-0.0.1-SNAPSHOT.jar ./planner.jar
ENV SPRING_PROFILES_ACTIVE=prod \
    PORT=10000 \
    JAVA_TOOL_OPTIONS="-Xms64m -Xmx256m -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=48m -XX:MaxDirectMemorySize=32m -Xss512k -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError -Djava.awt.headless=true -Dpdfbox.fontcache=/tmp"
USER 10001:10001
EXPOSE 10000
ENTRYPOINT ["java", "-jar", "/app/planner.jar"]
