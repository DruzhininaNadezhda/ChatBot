
# Используем официальный образ Java 17 (можно заменить на нужный)
FROM eclipse-temurin:17-jdk

# Создаём рабочую директорию в контейнере
WORKDIR /app

# Копируем jar-файл из target в контейнер
COPY target/*.jar app.jar

# Устанавливаем порт, который Render передаёт через переменную PORT
ENV PORT=8080
EXPOSE 8080

# Команда для запуска приложения
CMD ["java", "-jar", "app.jar"]
