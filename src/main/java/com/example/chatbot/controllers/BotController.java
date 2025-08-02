package com.example.chatbot.controllers;

import com.example.chatbot.bd.ApplicationRepository;
import com.example.chatbot.dto.ApplicationDTO;
import com.example.chatbot.service.EmailService;
import com.example.chatbot.service.FileAttachService;
import com.example.chatbot.service.GigaChatClientService;
import com.example.chatbot.service.JsonParserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class BotController {

    private final GigaChatClientService gigaChatClient;
    private final JsonParserService parserService;
    private final FileAttachService fileAttachService;
    private final ApplicationRepository applicationRepository;
    private final EmailService emailService;

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
            String emailBody = emailService.sendApplication(dto); // ⬅ всё ушло сюда

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
}
