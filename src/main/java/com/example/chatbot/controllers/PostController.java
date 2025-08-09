package com.example.chatbot.controllers;
import com.example.chatbot.bd.ApplicationRepository;
import com.example.chatbot.components.AttachmentRegistry;
import com.example.chatbot.dto.ApplicationDTO;
import com.example.chatbot.dto.AttachmentView;
import com.example.chatbot.service.EmailService;
import com.example.chatbot.service.FileAttachService;
import com.example.chatbot.service.GigaChatClientService;
import com.example.chatbot.service.JsonParserService;
import com.example.chatbot.service.in.EmailIn;
import com.example.chatbot.service.out.EmailOut;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class PostController {
    private final EmailIn emailIn;
    private final EmailService emailService;
    private final EmailOut emailOut;
    private final ApplicationRepository applicationRepository;
    private final GigaChatClientService gigaChatClientService;
    private final AttachmentRegistry registry;
    private final JsonParserService parserService;
    private final FileAttachService fileAttachService;


    // вместо "переслано" — отдаём список вложений для фронта
    @PostMapping("/check-inbox")
    public ResponseEntity<?> checkInboxFromUser(@RequestParam String senderEmail) {
        try {
            List<AttachmentView> list = emailIn.extractAndReturnAttachments(senderEmail);

            // ⬇️ запускаем асинхронную отправку в GigaChat, чтобы не блокировать фронт
            new Thread(() -> gigaChatClientService.processWithGigaChatSilently(list)).start();

            return ResponseEntity.ok(Map.of(
                    "ok", true,
                    "count", list.size(),
                    "attachments", list
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "ok", false,
                    "error", e.getMessage()
            ));
        }
    }

    @PostMapping("/mail/check")
    public ResponseEntity<?> checkMail(@RequestParam String applicationId) {
        ApplicationDTO dto = applicationRepository.getOrCreate(applicationId);

        // 1) проверяем наличие почты в DTO
        String email = dto.getUserEmail();
        if (email == null || email.isBlank()) {
            return ResponseEntity.ok(Map.of(
                    "ok", false,
                    "message", "В заявке нет почты. Напиши адрес в чат и снова нажми «Проверить почту»."
            ));
        }

        try {
            // 2) тянем вложения из входящих от этого адреса
            List<AttachmentView> attachments = emailIn.extractAndReturnAttachments(email);
            if (attachments == null || attachments.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                        "ok", true,
                        "message", "Писем с вложениями от " + email + " не найдено."
                ));
            }

            // 3) берём самое свежее (первое) вложение
            AttachmentView att = attachments.get(0);

            // url вида ".../mail/attachment/{token}" → вытаскиваем token
            String url = att.getUrl();
            String token = url.substring(url.lastIndexOf('/') + 1);

            // 4) берём реальный файл из реестра вложений
            AttachmentRegistry.Item item = registry.get(token);
            if (item == null || item.file == null || !item.file.exists()) {
                return ResponseEntity.ok(Map.of(
                        "ok", false,
                        "message", "Вложение по токену не найдено или протухло."
                ));
            }

            File realFile = item.file;
            String displayName = (att.getName() != null && !att.getName().isBlank())
                    ? att.getName()
                    : realFile.getName();

            // 5) прикрепляем файл к DTO (загрузка в GigaChat происходит внутри)
            fileAttachService.attachLocalFileToDto(realFile, displayName, dto);
            // теперь в dto.setGigaFileId(...) уже проставлен fileId

            // 6) прогоняем через Гигачат — используем уже загруженный fileId (без повторной загрузки)
            String prompt =
                    "Проанализируй приложенный документ и верни JSON с ключами: " +
                            "статья_ддс, получатель, контактное_лицо, дата_платежа, дата_поступления, прочая информация. " +
                            "Ответ ТОЛЬКО JSON.";
            String raw = gigaChatClientService.sendPromptWithAttachment(prompt, dto.getGigaFileId());

            // 7) дополняем DTO
            parserService.updateDtoFromGigaChatJson(raw, dto);

            // 8) сохраняем
            applicationRepository.save(dto.getApplicationId(),dto);

            // 9) ответ фронту
            return ResponseEntity.ok(Map.of(
                    "ok", true,
                    "message", "Файл получен из почты и проанализирован. Заявка дополнена.",
                    "attachment", att
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "ok", false,
                    "message", "Ошибка при проверке почты: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/finalize")
    public ResponseEntity<String> finalizeDto(@RequestParam String applicationId) {
        ApplicationDTO dto = applicationRepository.get(applicationId);
        if (dto == null) {
            return ResponseEntity.status(404).body("Не найдена заявка");
        }

        try {
            // 1. Формируем тело письма
            String emailBody = emailService.buildEmailText(dto);

            // 2. Отправляем письмо с файлом и телом
            emailOut.sendEmail(dto, emailBody);

            // 3. Финализируем заявку
            boolean success = applicationRepository.delete(applicationId);
            boolean deleted = gigaChatClientService.deleteFileFromGiga(dto.getGigaFileId());
            return success
                    ? ResponseEntity.ok("Заявка передана и письмо отправлено:\n\n" + emailBody)
                    : ResponseEntity.status(404).body("Не найдена заявка");

        } catch (RuntimeException e) {
            return ResponseEntity.status(400).body("⚠ Не удалось отправить письмо: " + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
