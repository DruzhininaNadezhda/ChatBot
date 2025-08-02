
# 1. Используем образ с Maven и JDK 17
FROM maven:3.8.7-eclipse-temurin-17 AS build

# 2. Копируем всё в контейнер и собираем
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# 3. Используем лёгкий образ с только JDK
FROM eclipse-temurin:17-jdk

# 4. Копируем собранный .jar из предыдущего контейнера
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# 5. Порт и запуск
ENV PORT=8080
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
