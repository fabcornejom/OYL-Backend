# ---------- Etapa 1: compilar ----------
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY . .
# Arregla los saltos de línea de Windows y compila el .jar (sin correr tests)
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -q package -DskipTests

# ---------- Etapa 2: ejecutar ----------
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]