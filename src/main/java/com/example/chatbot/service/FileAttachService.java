package com.example.chatbot.service;

import com.example.chatbot.dto.ApplicationDTO;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;

@Service
public class FileAttachService {

    public void attachFileToDto(MultipartFile uploadedFile, ApplicationDTO dto) throws IOException {
        if (uploadedFile == null || uploadedFile.isEmpty()) return;

        // сохраняем файл во временное хранилище (можно заменить на БД, MinIO и т.д.)
        File temp = File.createTempFile("upload_", "_" + uploadedFile.getOriginalFilename());
        uploadedFile.transferTo(temp);

        // обновляем DTO
        dto.setFile(temp);
        dto.setOriginalFileName(uploadedFile.getOriginalFilename());
    }
}

