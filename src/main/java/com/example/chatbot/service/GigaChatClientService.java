package com.example.chatbot.service;

import com.example.chatbot.components.AttachmentRegistry;
import com.example.chatbot.dto.AttachmentView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

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
import java.util.List;
import java.util.UUID;
import java.io.File;
import java.nio.file.Files;

@Service
@Validated
@RequiredArgsConstructor
public class GigaChatClientService {
    private final AttachmentRegistry registry;
    private static final String API_URL = "https://ngw.devices.sberbank.ru:9443/api/v2/oauth";
    private static final String YOUR_API_KEY = "ZDQ4ZTc1YzMtMDUwNi00ZDc1LTlmNWYtYzg1NzNlOTQyYjNjOmM4MDFlNzc4LWZiMjMtNGYxZi04ODQ2LTY0Y2JiNTE5ZDA1NA==";

    // ========== НОВОЕ: анализ локального файла тем же путём, что и при ручной загрузке ==========
    /**
     * Заливает локальный файл в GigaChat и делает chat completion с вложением.
     * Возвращает сырой JSON-ответ GigaChat (строка), чтобы потом отдать в парсер.
     */

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

        String tokenJson = generateToken();
        String accessToken = extractAccessToken(tokenJson);
        String safePrompt = escapeForJson(prompt);

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

        HttpClient client = http();
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
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .header("RqUID", uuid.toString())
                .header("Authorization","Basic "+ YOUR_API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString("scope=GIGACHAT_API_PERS"))
                .build();

        HttpClient client = http();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        int statusCode = response.statusCode();
        if (statusCode >= 200 && statusCode <= 299) {
            return response.body();
        } else {
            throw new IOException("Ошибка запроса: " + statusCode + ". Ответ сервера: " + response.body());
        }
    }

    private String extractAccessToken(String json) {
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

    public String enrichWithGigaChat(com.example.chatbot.dto.ApplicationDTO dto, String userInput) throws Exception {
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

    public String uploadFileToGiga(File file, String mime) throws Exception {
        System.out.println("[uploadFileToGiga] Старт загрузки файла: " + file.getAbsolutePath());

        String accessToken = extractAccessToken(generateToken());
        String boundary = "----gc-" + java.util.UUID.randomUUID();
        String filename = file.getName();
        if (mime == null || mime.isBlank()) mime = guessMime(filename);

        byte[] pre = (
                "--" + boundary + "\r\n" +
                        "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename.replace("\"", "%22") + "\"\r\n" +
                        "Content-Type: " + mime + "\r\n\r\n"
        ).getBytes(java.nio.charset.StandardCharsets.UTF_8);

        byte[] post = (
                "\r\n--" + boundary + "\r\n" +
                        "Content-Disposition: form-data; name=\"purpose\"\r\n\r\n" +
                        "general\r\n" +
                        "--" + boundary + "--\r\n"
        ).getBytes(java.nio.charset.StandardCharsets.UTF_8);

        byte[] fileBytes = java.nio.file.Files.readAllBytes(file.toPath());
        byte[] body = java.nio.ByteBuffer
                .allocate(pre.length + fileBytes.length + post.length)
                .put(pre).put(fileBytes).put(post).array();

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://gigachat.devices.sberbank.ru/api/v1/files"))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<String> resp = http().send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("Upload failed: " + resp.statusCode() + " " + resp.body());
        }

        String json = resp.body();
        int i = json.indexOf("\"id\":\"");
        if (i < 0) throw new IOException("No file id in response: " + json);
        int s = i + 6, e = json.indexOf('"', s);
        String fileId = json.substring(s, e);
        return fileId;
    }

    public String sendPromptWithAttachment(String prompt, String fileId) throws Exception {
        String accessToken = extractAccessToken(generateToken());
        String body = """
        {
          "model": "GigaChat-2-Max",
          "function_call": "auto",
          "messages": [
            {
              "role": "user",
              "content": "%s",
              "attachments": ["%s"]
            }
          ],
          "stream": false
        }
        """.formatted(escapeForJson(prompt), fileId);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://gigachat.devices.sberbank.ru/api/v1/chat/completions"))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> resp = http().send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("Chat failed: " + resp.statusCode() + " " + resp.body());
        }
        return resp.body();
    }

    public void processWithGigaChatSilently(List<AttachmentView> attachments) {
        if (attachments == null || attachments.isEmpty()) return;

        for (AttachmentView att : attachments) {
            try {
                String url = att.getUrl();
                String token = url.substring(url.lastIndexOf('/') + 1);

                AttachmentRegistry.Item item = registry.get(token);
                if (item == null || item.file == null || !item.file.exists()) {
                    System.err.println("[/check-inbox][AI][" + att.getName() + "] файл не найден/протух по токену: " + token);
                    continue;
                }

                String fileId = uploadFileToGiga(item.file, item.mimeType);
                String prompt =
                        "Проанализируй приложенный документ и верни JSON с ключами: " +
                                "статья_ддс, получатель, контактное_лицо, дата_платежа, дата_поступления, прочая информация. " +
                                "Ответ ТОЛЬКО JSON.";

                String raw = sendPromptWithAttachment(prompt, fileId);
                System.out.println("[/check-inbox][AI][" + att.getName() + "] RAW RESPONSE:\n" + raw);

            } catch (Exception ex) {
                System.err.println("[/check-inbox][AI][" + att.getName() + "] Ошибка: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
    }

    public boolean deleteFileFromGiga(String fileId) throws Exception {
        String accessToken = extractAccessToken(generateToken());

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://gigachat.devices.sberbank.ru/api/v1/files/" + fileId))
                .header("Authorization", "Bearer " + accessToken)
                .DELETE()
                .build();

        HttpResponse<String> resp = http().send(req, HttpResponse.BodyHandlers.ofString());
        return resp.statusCode() / 100 == 2;
    }

    private HttpClient http() throws Exception {
        return HttpClient.newBuilder()
                .sslContext(createInsecureSslContext())
                .build();
    }

    /** Анализ уже-локального файла: заливаем → делаем chat completion с вложением → возвращаем сырой ответ. */
    public String analyzeAttachment(String localPath) throws Exception {
        File file = new File(localPath);
        if (!file.isFile()) throw new IllegalArgumentException("Файл не найден: " + localPath);
        String mime = guessMime(file.getName());
        String fileId = uploadFileToGiga(file, mime);
        String prompt = "Проанализируй приложенный документ и верни JSON с ключами: " +
                "статья_ддс, получатель, контактное_лицо, дата_платежа, дата_поступления, прочая информация. " +
                "Ответ ТОЛЬКО JSON.";
        return sendPromptWithAttachment(prompt, fileId);
    }

    /** Определение MIME по расширению (используется, если probeContentType ничего не дал). */
    public static String guessMime(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".pdf"))  return "application/pdf";
        if (n.endsWith(".png"))  return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (n.endsWith(".doc"))  return "application/msword";
        if (n.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (n.endsWith(".xls"))  return "application/vnd.ms-excel";
        return "application/octet-stream";
    }

}
