package com.example.chatbot.service;

import com.example.chatbot.dto.ApplicationDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

@Service
public class JsonParserService {

    private final ObjectMapper mapper = new ObjectMapper();
    public void updateDtoFromGigaChatJson(String gigaChatResponse, ApplicationDTO dto) throws Exception {
        JsonNode root = mapper.readTree(gigaChatResponse);
        String content = root.path("choices").get(0).path("message").path("content").asText();
        String cleanedJson = extractJsonFromMarkdown(content);
        JsonNode result = mapper.readTree(cleanedJson);

        updateIfValid(result, "пользователь", dto::setUsername);
        updateIfValid(result, "статья_ддс", dto::setDds);
        updateIfValid(result, "получатель", dto::setReceiver);
        updateIfValid(result, "контактное_лицо", dto::setContact);
        updateIfValid(result, "дата_платежа", dto::setPaymentDate);
        updateIfValid(result, "дата_поступления", dto::setDeliveryDate);

        System.out.println("Обновлено DTO: " + dto);
    }

    private void updateIfValid(JsonNode json, String fieldName, Consumer<String> setter) {
        String value = json.path(fieldName).asText(null);
        if (value != null && !value.isBlank() && !value.equalsIgnoreCase("не заполнено")) {
            setter.accept(value);
        }
    }

    private String extractJsonFromMarkdown(String markdown) {
        if (markdown.contains("```")) {
            return markdown
                    .replaceAll("(?s)```json\\s*", "")
                    .replaceAll("(?s)```", "")
                    .trim();
        }
        return markdown.trim();
    }
}
