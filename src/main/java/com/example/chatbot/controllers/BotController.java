package com.example.chatbot.controllers;

import com.example.chatbot.bd.ApplicationRepository;
import com.example.chatbot.dto.ApplicationDTO;
import com.example.chatbot.service.EmailService;
import com.example.chatbot.service.FileAttachService;
import com.example.chatbot.service.GigaChatClientService;
import com.example.chatbot.service.JsonParserService;
import com.example.chatbot.service.in.EmailIn;
import com.example.chatbot.service.out.EmailOut;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class BotController {

    private final GigaChatClientService gigaChatClient;
    private final EmailIn emailIn;
    private final JsonParserService parserService;
    private final FileAttachService fileAttachService;
    private final ApplicationRepository applicationRepository;
    private final EmailService emailService;
    private final EmailOut emailOut;

    @PostMapping("/process")
    public String handleUserInput(
            @RequestParam("applicationId") String applicationId,
            @RequestParam(value = "message", required = false) String message,
            @RequestParam(value = "file", required = false) MultipartFile file) {

        try {
            ApplicationDTO dto = applicationRepository.getOrCreate(applicationId);

            if (file != null && !file.isEmpty()) {
                fileAttachService.attachFileToDto(file, dto);
                return "Файл получен: " + file.getOriginalFilename();
            }

            if (message != null && !message.isBlank()) {
                String gigaReply = gigaChatClient.enrichWithGigaChat(dto, message);
                parserService.updateDtoFromGigaChatJson(gigaReply, dto);

                System.out.println("Ответ GigaChat:\n" + gigaReply);
                System.out.println("Текущее DTO:\n" + dto);

                return gigaReply;
            }
            return "Пустой запрос";
        } catch (Exception ex) {
            ex.printStackTrace();
            return "Ошибка: " + ex.getMessage();
        }
    }
    @GetMapping("/get-dto")
    public ResponseEntity<ApplicationDTO> getDto(
            @RequestParam String applicationId) {
        ApplicationDTO dto = applicationRepository.get(applicationId);
        if (dto == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/clear-dto")
    public void clearDto(
            @RequestParam String applicationId) {
        applicationRepository.clear(applicationId);
    }

    @GetMapping("/check-dto")
    public String checkDto(
            @RequestParam String applicationId) {
        return applicationRepository.isReady(applicationId) ? "OK" : "NO";
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
            boolean success = applicationRepository.finalizeApplication(applicationId);
            return success
                    ? ResponseEntity.ok("Заявка передана и письмо отправлено:\n\n" + emailBody)
                    : ResponseEntity.status(404).body("Не найдена заявка");

        } catch (RuntimeException e) {
            return ResponseEntity.status(400).body("⚠ Не удалось отправить письмо: " + e.getMessage());
        }
    }

    @PostMapping("/set-username")
    public ResponseEntity<Void> setUsername(@RequestParam String applicationId,
                                            @RequestParam String username) {
        applicationRepository.setUsername(applicationId, username);
        return ResponseEntity.ok().build();
    }
    @PostMapping("/add-comment")
    public ResponseEntity<Void> addComment(
            @RequestParam String applicationId,
            @RequestParam String comment) {
        applicationRepository.addComment(applicationId, comment);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/check-inbox")
    public ResponseEntity<String> checkInboxFromUser(@RequestParam String senderEmail) {
        try {
            emailIn.extractAndForwardAttachment(senderEmail);
            return ResponseEntity.ok("Вложение от " + senderEmail + " переслано.");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Ошибка: " + e.getMessage());
        }
    }

    // POST /set-user-email
    @PostMapping("/set-user-email")
    public void setUserEmail(@RequestParam String applicationId,
                             @RequestParam String userEmail) {
        var dto = applicationRepository.getOrCreate(applicationId);
        dto.setUserEmail(userEmail);
    }
    // GET /dto-head?applicationId=...
    @GetMapping("/dto-head")
    public Map<String, String> dtoHead(@RequestParam String applicationId) {
        var dto = applicationRepository.getOrCreate(applicationId);
        return Map.of(
                "username", dto.getUsername() == null ? "" : dto.getUsername(),
                "userEmail", dto.getUserEmail() == null ? "" : dto.getUserEmail()
        );
    }
}
