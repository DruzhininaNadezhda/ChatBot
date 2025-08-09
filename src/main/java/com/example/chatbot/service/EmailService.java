package com.example.chatbot.service;

import com.example.chatbot.dto.ApplicationDTO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EmailService {

    public String buildEmailText(ApplicationDTO dto) {
        StringBuilder sb = new StringBuilder();
        sb.append("Заявка № ").append(dto.getApplicationId()).append("\n");
        sb.append("Имя отправителя: ").append(dto.getUsername()).append("\n");
        sb.append("Получатель: ").append(dto.getReceiver()).append("\n");
        sb.append("Статья ДДС: ").append(dto.getDds()).append("\n");
        sb.append("Контактное лицо: ").append(dto.getContact()).append("\n");
        sb.append("Дата платежа: ").append(dto.getPaymentDate()).append("\n");
        sb.append("Дата поступления: ").append(dto.getDeliveryDate()).append("\n");

        List<String> comments = dto.getComments();
        if (comments != null && !comments.isEmpty()) {
            sb.append("\nКомментарии:\n");
            for (String comment : comments) {
                sb.append("– ").append(comment).append("\n");
            }
        }

        return sb.toString();
    }
}



