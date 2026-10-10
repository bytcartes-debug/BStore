# Estágio 1: Build do frontend React
FROM node:20-alpine AS frontend-build
WORKDIR /app/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
ENV DOCKER_BUILD=true
RUN npm run build

# Estágio 2: Build do backend Java com Maven
FROM maven:3.9-eclipse-temurin-17 AS backend-build
WORKDIR /app
COPY pom.xml ./
RUN mvn dependency:go-offline -q
COPY src/ ./src/
COPY --from=frontend-build /app/frontend/dist/ ./src/main/resources/public/
RUN mvn clean package -q -DskipTests

# Estágio 3: Imagem final leve de execução
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=backend-build /app/target/barraca-sistema-1.0.jar app.jar

EXPOSE 8080
CMD ["java", "-Djava.awt.headless=true", "-jar", "app.jar"]
