package com.example.chatbot.service.in;

import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import jakarta.mail.util.ByteArrayDataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.*;
import java.nio.file.Files;
import java.security.cert.X509Certificate;
import java.util.Properties;

@Service
//@RequiredArgsConstructor
public class EmailIn {

    @Value("${mail.username}")
    private String botEmail;

    @Value("${mail.password}")
    private String botPassword;

    @Value("${mail.to}")
    private String forwardTo;

    public void extractAndForwardAttachment(String senderEmail) {
        File tempFile = null;

        try {
            // 1. Подключение к почте
            Properties props = new Properties();
            props.put("mail.store.protocol", "imaps");
            Session session = Session.getInstance(props);
            Store store = session.getStore("imaps");
            store.connect("imap.yandex.ru", botEmail, botPassword);

            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);
            Message[] messages = inbox.getMessages();

            // 2. Поиск нужного письма
            for (int i = messages.length - 1; i >= 0 && i >= messages.length - 30; i--) {
                Message message = messages[i];

                Address[] fromAddresses = message.getFrom();
                String from = (fromAddresses != null && fromAddresses.length > 0)
                        ? fromAddresses[0].toString()
                        : "неизвестно";

                System.out.println("Письмо #" + i + " от " + from);

                if (!from.contains(senderEmail)) continue;

                System.out.println("Пробуем прочитать контент письма #" + i);
                Object content = message.getContent();
                System.out.println("Успешно получили content: " + content.getClass().getName());
                if (!(content instanceof Multipart multipart)) {
                    System.out.println("⚠ Не multipart письмо — вложений нет");
                    continue;
                }

                // 3. Поиск вложения
                for (int j = 0; j < multipart.getCount(); j++) {
                    BodyPart part = multipart.getBodyPart(j);

                    if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
                        String rawName = part.getFileName();
                        String decodedName = MimeUtility.decodeText(rawName);
                        String safeName = decodedName.replaceAll("[^a-zA-Z0-9\\.\\-_]", "_");

                        // 4. Сохранение во временный файл
                        tempFile = File.createTempFile("mail_attach_", "_" + safeName);
                        try (
                                InputStream is = part.getInputStream();
                                FileOutputStream fos = new FileOutputStream(tempFile)
                        ) {
                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            while ((bytesRead = is.read(buffer)) != -1) {
                                fos.write(buffer, 0, bytesRead);
                            }

                            // 5. Пересылка
                            sendEmailWithAttachment(decodedName, tempFile);
                            System.out.println("✅ Вложение переслано: " + decodedName);
                            return;
                        }
                    }
                }

                System.out.println("❌ Вложения не найдено в письме #" + i);
            }

            inbox.close(false);
            store.close();

        } catch (Exception e) {
            System.err.println("Ошибка при обработке: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (tempFile != null && tempFile.exists()) {
                if (tempFile.delete()) {
                    System.out.println("🗑 Временный файл удалён");
                } else {
                    System.err.println("⚠ Не удалось удалить временный файл: " + tempFile.getAbsolutePath());
                }
            }
        }
    }

    private void sendEmailWithAttachment(String originalName, File attachment) {
        try {
            Properties props = new Properties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.host", "smtp.yandex.ru");
            props.put("mail.smtp.port", "465");

            Session session = Session.getInstance(props, new Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(botEmail, botPassword);
                }
            });

            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(botEmail));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(forwardTo));
            message.setSubject("Вложение от " + botEmail);

            Multipart multipart = new MimeMultipart();

            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setText("Файл из письма.", "UTF-8");
            multipart.addBodyPart(textPart);

            MimeBodyPart attachPart = new MimeBodyPart();

            byte[] fileBytes = Files.readAllBytes(attachment.toPath());
            String mimeType = Files.probeContentType(attachment.toPath());
            if (mimeType == null) mimeType = "application/octet-stream";

            DataSource source = new ByteArrayDataSource(fileBytes, mimeType);
            attachPart.setDataHandler(new DataHandler(source));
            attachPart.setFileName(MimeUtility.encodeText(originalName, "UTF-8", null));

            multipart.addBodyPart(attachPart);

            message.setContent(multipart);
            disableSslVerification();
            Transport.send(message);

            System.out.println("✉ Вложение успешно отправлено: " + originalName);

        } catch (Exception e) {
            throw new RuntimeException("Ошибка отправки вложения: " + e.getMessage(), e);
        }
    }
    private void disableSslVerification() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                        public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                    }
            };

            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            SSLContext.setDefault(sc);
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        } catch (Exception e) {
            System.err.println("Не удалось отключить SSL-проверку");
        }
    }

}

// package com.example.chatbot.service.in;
//
//import jakarta.activation.DataHandler;
//import jakarta.activation.DataSource;
//import jakarta.mail.*;
//import jakarta.mail.internet.*;
//import jakarta.mail.util.ByteArrayDataSource;
//import lombok.RequiredArgsConstructor;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Service;
//
//import javax.net.ssl.HttpsURLConnection;
//import javax.net.ssl.SSLContext;
//import javax.net.ssl.TrustManager;
//import javax.net.ssl.X509TrustManager;
//import java.io.*;
//import java.nio.file.Files;
//import java.security.cert.X509Certificate;
//import java.util.Arrays;
//import java.util.Properties;
//
//@Service
////@RequiredArgsConstructor
//public class EmailIn {
//
//    @Value("${mail.username}")
//    private String botEmail;
//
//    @Value("${mail.password}")
//    private String botPassword;
//
//    @Value("${mail.to}")
//    private String forwardTo;
//
//    Message[] messages;
//    Object content;
//    File tempFile;
//
//    private void emailIn() {
//        Properties props = new Properties();
//        props.put("mail.store.protocol", "imaps");
//        Session session = Session.getInstance(props);
//
//        try (
//                Store store = session.getStore("imaps");
//        ) {
//            store.connect("imap.yandex.ru", botEmail, botPassword);
//
//            try (
//                    Folder inbox = store.getFolder("INBOX");
//            ) {
//                inbox.open(Folder.READ_ONLY);
//                messages = inbox.getMessages();
//                // Обработка сообщений
//            }
//        } catch (Exception e) {
//            System.err.println("Ошибка при обработке: " + e.getMessage());
//            e.printStackTrace();
//        }
//    }
//
//
//    public void extractAndForwardAttachment(String senderEmail) {
//
//        try {
//            // 2. Поиск нужного письма
//            emailIn();
//            for (int i = messages.length - 1; i >= 0 && i >= messages.length - 30; i--) {
//                Message message = messages[i];
//                Address[] fromAddresses = message.getFrom(); // Здесь падает
//
//                String from = (fromAddresses != null && fromAddresses.length > 0)
//                        ? fromAddresses[0].toString()
//                        : "неизвестно";
//
//                System.out.println("Письмо #" + i + " от " + from);
//
//                if (!from.contains(senderEmail)) continue;
//
//                System.out.println("Пробуем прочитать контент письма #" + i);
//                content = message.getContent();
//                System.out.println("Успешно получили content: " + content.getClass().getName());
//                extractAndForwardFile(content);
//            }
//        } catch (MessagingException e) {
//            System.out.println(e.getCause()+ "11"); // Вот сюда падает!!!!!
//            throw new RuntimeException(e);
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//    }
//
//    public void extractAndForwardFile(Object content) throws MessagingException {
//        try {
//            if (!(content instanceof Multipart multipart)) {
//                System.out.println("⚠ Не multipart письмо — вложений нет");
//                return;
//            }
//            // 3. Поиск вложения
//            for (int j = 0; j < multipart.getCount(); j++) {
//                BodyPart part = multipart.getBodyPart(j);
//
//                if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
//                    String rawName = part.getFileName();
//                    String decodedName = MimeUtility.decodeText(rawName);
//                    String safeName = decodedName.replaceAll("[^a-zA-Z0-9\\.\\-_]", "_");
//
//                    // 4. Сохранение во временный файл
//                    tempFile = File.createTempFile("mail_attach_", "_" + safeName);
//                    try (
//                            InputStream is = part.getInputStream();
//                            FileOutputStream fos = new FileOutputStream(tempFile)
//                    ) {
//                        byte[] buffer = new byte[4096];
//                        int bytesRead;
//                        while ((bytesRead = is.read(buffer)) != -1) {
//                            fos.write(buffer, 0, bytesRead);
//                        }
//
//                        System.out.println("❌ Вложения не найдено в письме #");
//
//                    } catch (FileNotFoundException e) {
//                        throw new RuntimeException(e);
//                    } catch (UnsupportedEncodingException e) {
//                        throw new RuntimeException(e);
//
//                    } catch (IOException e) {
//                        throw new RuntimeException(e);
//                    } catch (MessagingException e) {
//                        throw new RuntimeException(e);
//                    } finally {
//                        if (tempFile != null && tempFile.exists()) {
//                            if (tempFile.delete()) {
//                                System.out.println("🗑 Временный файл удалён");
//                            } else {
//                                System.err.println("⚠ Не удалось удалить временный файл: " + tempFile.getAbsolutePath());
//                            }
//                        }
//                    }
//                }
//            }
//        } catch (UnsupportedEncodingException e) {
//            throw new RuntimeException(e);
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//    }
//}
////    private void sendEmailWithAttachment(String originalName, File attachment) {
////        try {
////            Properties props = new Properties();
////            props.put("mail.smtp.auth", "true");
////            props.put("mail.smtp.ssl.enable", "true");
////            props.put("mail.smtp.host", "smtp.yandex.ru");
////            props.put("mail.smtp.port", "465");
////
////            Session session = Session.getInstance(props, new Authenticator() {
////                protected PasswordAuthentication getPasswordAuthentication() {
////                    return new PasswordAuthentication(botEmail, botPassword);
////                }
////            });
////
////            MimeMessage message = new MimeMessage(session);
////            message.setFrom(new InternetAddress(botEmail));
////            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(forwardTo));
////            message.setSubject("Вложение от " + botEmail);
////
////            Multipart multipart = new MimeMultipart();
////
////            MimeBodyPart textPart = new MimeBodyPart();
////            textPart.setText("Файл из письма.", "UTF-8");
////            multipart.addBodyPart(textPart);
////
////            MimeBodyPart attachPart = new MimeBodyPart();
////
////            byte[] fileBytes = Files.readAllBytes(attachment.toPath());
////            String mimeType = Files.probeContentType(attachment.toPath());
////            if (mimeType == null) mimeType = "application/octet-stream";
////
////            DataSource source = new ByteArrayDataSource(fileBytes, mimeType);
////            attachPart.setDataHandler(new DataHandler(source));
////            attachPart.setFileName(MimeUtility.encodeText(originalName, "UTF-8", null));
////
////            multipart.addBodyPart(attachPart);
////
////            message.setContent(multipart);
////            disableSslVerification();
////            Transport.send(message);
////
////            System.out.println("✉ Вложение успешно отправлено: " + originalName);
////
////        } catch (Exception e) {
////            throw new RuntimeException("Ошибка отправки вложения: " + e.getMessage(), e);
////        }
////    }
////    private void disableSslVerification() {
////        try {
////            TrustManager[] trustAllCerts = new TrustManager[]{
////                    new X509TrustManager() {
////                        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
////                        public void checkClientTrusted(X509Certificate[] certs, String authType) {}
////                        public void checkServerTrusted(X509Certificate[] certs, String authType) {}
////                    }
////            };
////
//            SSLContext sc = SSLContext.getInstance("TLS");
//            sc.init(null, trustAllCerts, new java.security.SecureRandom());
//            SSLContext.setDefault(sc);
//            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
//            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
//        } catch (Exception e) {
//            System.err.println("Не удалось отключить SSL-проверку");
//        }
//    }
