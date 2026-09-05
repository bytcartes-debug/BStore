# Imagem simples: só compila o Java com os assets React já pré-compilados
FROM maven:3.9-eclipse-temurin-11-slim AS build
WORKDIR /app

# Copia o pom.xml e baixa dependências (cache layer)
COPY pom.xml ./
RUN mvn dependency:go-offline -q

# Copia o código Java e os assets do React já compilados
COPY src/ ./src/

# Compila o JAR final
RUN mvn clean package -q -DskipTests

# Imagem final leve
FROM eclipse-temurin:11-jre-alpine
WORKDIR /app
COPY --from=build /app/target/barraca-sistema-1.0.jar app.jar

EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
