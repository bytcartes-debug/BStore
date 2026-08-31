# ── Etapa 1: Build do frontend React ────────────────────────────────
FROM node:20-alpine AS frontend-build
WORKDIR /frontend
COPY frontend/package*.json ./
RUN npm install
COPY frontend/ ./
# DOCKER_BUILD=1 faz o Vite compilar para /frontend/dist
ENV DOCKER_BUILD=1
RUN npm run build

# ── Etapa 2: Build do backend Java ──────────────────────────────────
FROM maven:3.9-eclipse-temurin-11 AS java-build
WORKDIR /app
COPY pom.xml ./
COPY src/ ./src/
# Copia o React compilado para os resources do Java (onde o Javalin serve os ficheiros estáticos)
COPY --from=frontend-build /frontend/dist/ ./src/main/resources/public/
RUN mvn clean package -q -DskipTests

# ── Etapa 3: Imagem final leve ───────────────────────────────────────
FROM eclipse-temurin:11-jre-alpine
WORKDIR /app
COPY --from=java-build /app/target/barraca-sistema-1.0.jar app.jar

EXPOSE 8080

CMD ["java", "-jar", "app.jar"]
