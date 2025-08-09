package com.example.chatbot.service;

import com.example.chatbot.dto.ApplicationDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;

@Service
@RequiredArgsConstructor
public class FileAttachService {

    private final GigaChatClientService gigaChatClientService;

    /** Пользователь загрузил файл вручную (MultipartFile). */
    public void attachFileToDto(MultipartFile uploadedFile, ApplicationDTO dto) throws Exception {
        if (uploadedFile == null || uploadedFile.isEmpty()) return;

        // 1) сохраняем во временный файл
        File temp = File.createTempFile("upload_", "_" + uploadedFile.getOriginalFilename());
        uploadedFile.transferTo(temp);

        // 2) обновляем DTO
        dto.setFile(temp);
        dto.setOriginalFileName(uploadedFile.getOriginalFilename());

        // 3) MIME и загрузка в GigaChat → запоминаем fileId для /finalize
        String mime = Files.probeContentType(temp.toPath());
        if (mime == null || mime.isBlank()) mime = GigaChatClientService.guessMime(temp.getName());
        String fileId = gigaChatClientService.uploadFileToGiga(temp, mime);
        dto.setGigaFileId(fileId);
    }

    /** Файл уже лежит локально (например, вытащили из почты). */
    public void attachLocalFileToDto(File file, String originalName, ApplicationDTO dto) throws Exception {
        if (file == null || !file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("Файл не найден или не обычный: " + file);
        }

        // 1) обновляем DTO
        dto.setFile(file);
        dto.setOriginalFileName(
                (originalName != null && !originalName.isBlank()) ? originalName : file.getName()
        );

        // 2) MIME и загрузка в GigaChat → запоминаем fileId для /finalize
        String mime = Files.probeContentType(file.toPath());
        if (mime == null || mime.isBlank()) mime = GigaChatClientService.guessMime(file.getName());
        String fileId = gigaChatClientService.uploadFileToGiga(file, mime);
        dto.setGigaFileId(fileId);
    }
}
