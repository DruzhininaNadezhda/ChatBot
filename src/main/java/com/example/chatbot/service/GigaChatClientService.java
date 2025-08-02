package com.example.chatbot.service;

import com.example.chatbot.dto.ApplicationDTO;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.UUID;

@Service
@Validated
public class GigaChatClientService {

    private static final String API_URL = "https://ngw.devices.sberbank.ru:9443/api/v2/oauth"; // подставьте правильный адрес API
    private static final String YOUR_API_KEY = "ZDQ4ZTc1YzMtMDUwNi00ZDc1LTlmNWYtYzg1NzNlOTQyYjNjOmM4MDFlNzc4LWZiMjMtNGYxZi04ODQ2LTY0Y2JiNTE5ZDA1NA=="; // вставьте сюда полученный токен
    public String sendPrompt(String pr) throws Exception {
        String prompt = "Ты помощник бухгалтерии, который должен собрать данные для формирования заявки на оплату. Используй только данные пользователя, не добавляй ничего от себя." +
                "В запросе пользователя (входных данных) будут указаны следующие данные: " +
                "- Статья ДДС (число до 8 знаков);" +
                "- Получатель - наименование контрагента и его ИНН. ИНН может отсутствовать. ИНН РФ может быть только от 10 чисел. Если в сообщении найден ИНН, но в поле «получатель» он отсутствует или другой— добавь ИНН прямо в это поле (в скобках после наименования)." +
                "- контактное лицо. Может быть в виде фамилии с телефоном и почтой, может быть только почта или телефон без фамилии. Если в сообщении есть телефон или почта, но в поле «контактное лицо» они отсутствуют — добавь их туда (через запятую, после имени, если имя есть)." +
                "- Дата платежа" +
                "- Дата планируемого поступления товаров (услуг)" +
                "Часть данных может быть заполнена раннее и поступает в уже обработанном виде" +
                "Твоя задача выделить из текста недостающие данные и вернуть в формате json и уже имеющиеся данные и недостающие." +
                "Формат ответа только в json, без дополнительного текста, если данные отсутствуют, передавай пустые значения не прописывай \"данные отсутствуют\" и тому подобное:" +
                "{" +
                "\"статья_ддс\": (данные)," +
                "\"получатель\": \"(данные)\"," +
                "\"контактное_лицо\": \"(данные)\"," +
                "\"дата_платежа\": \"(данные)\"," +
                "\"дата_поступления\": \"(данные)\"" +
                "\"прочая информация\": \"(данные)\"" +
                "}"+pr;
        // Шаг 1: получить access_token
        String tokenJson = generateToken();
        String accessToken = extractAccessToken(tokenJson);
        String safePrompt = escapeForJson(prompt);
        // Шаг 2: тело запроса
        String requestBody = """
        {
          "model": "GigaChat:latest",
          "messages": [
            {
              "role": "user",
              "content": "%s"
            }
          ],
          "temperature": 1,
          "top_p": 0.1,
          "n": 1,
          "stream": false,
          "max_tokens": 512,
          "repetition_penalty": 1
        }
        """.formatted(safePrompt);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://gigachat.devices.sberbank.ru/api/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + accessToken)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpClient client = HttpClient.newBuilder()
                .sslContext(createInsecureSslContext())
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 200 && response.statusCode() <= 299) {
            return response.body();
        } else {
            throw new IOException("Ошибка запроса: " + response.statusCode() + ". Ответ сервера: " + response.body());
        }
    }
    public String generateToken() throws Exception {
        UUID uuid = UUID.randomUUID();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))                     // адрес API
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")   // контент типа JSON
                .header("RqUID", uuid.toString())
                .header("Authorization","Basic "+ YOUR_API_KEY) // добавляем токен
                .POST(HttpRequest.BodyPublishers.ofString("scope=GIGACHAT_API_PERS")) // тело запроса
                .build();

        HttpClient client = HttpClient.newBuilder()
                .sslContext(createInsecureSslContext())
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        int statusCode = response.statusCode();
        if (statusCode >= 200 && statusCode <= 299) { // успешный статус ответа
            return response.body(); // возвращаем строку с ответом
        } else {
            throw new IOException("Ошибка запроса: " + statusCode + ". Ответ сервера: " + response.body());
        }
    }
    private String extractAccessToken(String json) {
        // Предположим, что в JSON есть строка: "access_token":"..."
        int start = json.indexOf("\"access_token\":\"");
        if (start == -1) return null;

        start += "\"access_token\":\"".length();
        int end = json.indexOf("\"", start);
        if (end == -1) return null;

        return json.substring(start, end);
    }
    private String escapeForJson(String text) {
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "")
                .replace("\t", "\\t");
    }
    private SSLContext createInsecureSslContext() throws Exception {
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                }
        };

        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, trustAllCerts, new SecureRandom());
        return sc;
    }
    public String enrichWithGigaChat(ApplicationDTO dto, String userInput) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("Номер заявки: ").append(dto.getApplicationId()).append("\n");
        if (dto.getDds() != null) sb.append("ДДС: ").append(dto.getDds()).append("\n");
        if (dto.getReceiver() != null) sb.append("Получатель: ").append(dto.getReceiver()).append("\n");
        if (dto.getContact() != null) sb.append("Контакт: ").append(dto.getContact()).append("\n");
        if (dto.getPaymentDate() != null) sb.append("Дата платежа: ").append(dto.getPaymentDate()).append("\n");
        if (dto.getDeliveryDate() != null) sb.append("Дата поступления: ").append(dto.getDeliveryDate()).append("\n");

        String fullPrompt = "Вот текущая заявка: " + sb + "Пользователь ввёл: " + userInput;

        return sendPrompt(fullPrompt);
    }
}

