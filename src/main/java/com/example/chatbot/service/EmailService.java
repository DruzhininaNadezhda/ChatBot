package com.example.chatbot.service;

import com.example.chatbot.dto.ApplicationDTO;
import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.activation.FileDataSource;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import jakarta.mail.util.ByteArrayDataSource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Properties;
@Service
public class EmailService {

    public String sendApplication(ApplicationDTO dto) {
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

        File file = dto.getFile();
        String fileName = dto.getOriginalFileName();

        if (file != null && file.exists()) {
            sendFile(file, fileName, sb.toString());
        } else {
            throw new RuntimeException("Файл отсутствует или не найден на диске.");
        }

        return sb.toString(); // ⬅ вернём текст, чтобы использовать в ResponseEntity
    }

    public void sendFile(File file, String originalName, String text) {
        final String from = "wulvjxe@gmail.com";
        final String password = "yqzp nqea otdk onci";
        final String to = "druzhinina@itabirit.ru";

        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");

        Session session = Session.getInstance(props, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(from, password);
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject("Файл от чат-бота");

            Multipart multipart = new MimeMultipart();


            // Текст
            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setText(text, "UTF-8");
            MimeBodyPart filePart = new MimeBodyPart();

            // Загружаем файл в память
            byte[] fileBytes = Files.readAllBytes(file.toPath());

            // Определим MIME по расширению (или задаём вручную, если нужно)
            String mimeType = Files.probeContentType(file.toPath());
            if (mimeType == null) {
                mimeType = "application/octet-stream"; // по умолчанию
            }

            DataSource source = new ByteArrayDataSource(fileBytes, mimeType);
            filePart.setDataHandler(new DataHandler(source));
            filePart.setFileName(MimeUtility.encodeText(originalName, "UTF-8", null));
            multipart.addBodyPart(textPart);
            multipart.addBodyPart(filePart);
            message.setContent(multipart);

            Transport.send(message);
            System.out.println("✅ Отправлено с файлом: " + originalName);
        } catch (Exception e) {
            System.err.println("❌ Ошибка при отправке: ");
            e.printStackTrace();
        }
    }
}