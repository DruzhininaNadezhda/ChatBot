package com.example.chatbot.service.out;

import com.example.chatbot.dto.ApplicationDTO;
import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import jakarta.mail.util.ByteArrayDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.net.ssl.*;
import java.io.File;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Properties;

@Service
public class EmailOut {

    @Value("${mail.username}")
    private String from;

    @Value("${mail.password}")
    private String password;

    @Value("${mail.to}")
    private String to;

    public void sendEmail(ApplicationDTO dto, String text) {
        File file = dto.getFile();
        String originalName = dto.getOriginalFileName();

        if (file == null || !file.exists()) {
            throw new RuntimeException("Файл отсутствует или не найден на диске.");
        }

        String subject = "Заявка № " + dto.getApplicationId();

        try {
            Properties props = new Properties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.ssl.enable", "true"); // SSL вместо STARTTLS
            props.put("mail.smtp.host", "smtp.yandex.ru");
            props.put("mail.smtp.port", "465");

            Session session = Session.getInstance(props, new Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(from, password);
                }
            });

            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject(subject);

            Multipart multipart = new MimeMultipart();

            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setText(text, "UTF-8");
            multipart.addBodyPart(textPart);

            MimeBodyPart filePart = new MimeBodyPart();
            byte[] fileBytes = Files.readAllBytes(file.toPath());
            String mimeType = Files.probeContentType(file.toPath());
            if (mimeType == null) {
                mimeType = "application/octet-stream";
            }

            DataSource source = new ByteArrayDataSource(fileBytes, mimeType);
            filePart.setDataHandler(new DataHandler(source));
            filePart.setFileName(MimeUtility.encodeText(originalName, "UTF-8", null));
            multipart.addBodyPart(filePart);

            message.setContent(multipart);
            Transport.send(message);

            System.out.println("✅ Письмо отправлено: " + originalName);

            // ⬇️ Сохраняем копию в Отправленные
            saveToSentFolder(from, password, message);

        } catch (Exception e) {
            System.err.println("❌ Ошибка при отправке письма:");
            e.printStackTrace();
            throw new RuntimeException("Отправка письма не удалась: " + e.getMessage());
        }
    }

    private void saveToSentFolder(String username, String password, Message message) {
        try {
            disableSslVerification(); // ← временно отключаем SSL-проверку

            Properties props = new Properties();
            props.put("mail.store.protocol", "imaps");

            Session session = Session.getInstance(props);
            Store store = session.getStore("imaps");
            store.connect("imap.yandex.ru", username, password);

            Folder sent = store.getFolder("Sent");

            if (!sent.exists()) {
                for (Folder folder : store.getDefaultFolder().list()) {
                    System.out.println("▶ " + folder.getFullName()); // диагностика
                }
                throw new MessagingException("❌ Папка 'Отправленные' не найдена. Попробуй вручную указать имя.");
            }

            sent.open(Folder.READ_WRITE);
            sent.appendMessages(new Message[]{message});
            sent.close(false);
            store.close();

            System.out.println("📩 Письмо сохранено в 'Отправленные'");

        } catch (Exception e) {
            System.err.println("❌ Не удалось сохранить письмо в 'Отправленные':");
            e.printStackTrace();
        }
    }

    // ⚠ Для отладки. В продакшене обязательно убрать!
    private void disableSslVerification() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                        public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                    }
            };

            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new SecureRandom());
            SSLContext.setDefault(sc);

            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось отключить проверку SSL", e);
        }
    }
}

