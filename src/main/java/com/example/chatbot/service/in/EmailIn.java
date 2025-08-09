package com.example.chatbot.service.in;

import com.example.chatbot.components.AttachmentRegistry;
import com.example.chatbot.dto.AttachmentView;
import jakarta.activation.DataHandler;
import jakarta.mail.*;
import jakarta.mail.internet.MimeUtility;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

@Service
@RequiredArgsConstructor
public class EmailIn {

    @Value("${mail.username}") private String botEmail;
    @Value("${mail.password}") private String botPassword;

    private final AttachmentRegistry registry;

    /** ПУБЛИЧНЫЙ: ищем письмо и возвращаем список вложений (ссылки для чата) */
    public List<AttachmentView> extractAndReturnAttachments(String senderEmail) {
        List<AttachmentView> views = new ArrayList<>();
        Attachment att = null;

        try (StoreResource store = connectImap();
             FolderResource inbox = openInbox(store)) {

            Message msg = findLatestFromSender(inbox.get(), senderEmail, 30);
            if (msg == null) return views;

            // берём первое вложение (как в твоей логике). Нужно все — скажешь, сделаю extractAll
            att = extractFirstAttachment(msg);
            if (att == null) return views;

            String mime = Files.probeContentType(att.file.toPath());
            if (mime == null) mime = "application/octet-stream";
            long size = att.file.length();

            String token = registry.register(att.file, att.originalName, mime, size);
            String url = "/mail/attachment/" + token;

            views.add(new AttachmentView(att.originalName, mime, size, url));

            // файл оставляем живым — нужен для GET /mail/attachment/{token}
            att = null;
            return views;

        } catch (Exception e) {
            e.printStackTrace();
            return views;
        } finally {
            // если что-то пошло не так — подчистим временный файл
            if (att != null && att.file != null && att.file.exists()) {
                att.file.delete();
            }
        }
    }

    // === 1) Подключение к IMAP ===
    private StoreResource connectImap() throws MessagingException {
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", "imap.yandex.ru");
        props.put("mail.imaps.port", "993");
        props.put("mail.imaps.ssl.enable", "true");

        Session session = Session.getInstance(props);
        Store store = session.getStore("imaps");
        store.connect("imap.yandex.ru", botEmail, botPassword);
        return new StoreResource(store);
    }

    private FolderResource openInbox(StoreResource store) throws MessagingException {
        Folder inbox = store.get().getFolder("INBOX");
        inbox.open(Folder.READ_ONLY);
        return new FolderResource(inbox);
    }

    // === 2) Поиск письма среди последних N ===
    private Message findLatestFromSender(Folder inbox, String senderEmail, int lookBackCount) throws MessagingException {
        int total = inbox.getMessageCount();
        if (total == 0) return null;

        int from = Math.max(1, total - lookBackCount + 1);
        Message[] tail = inbox.getMessages(from, total);

        for (int i = tail.length - 1; i >= 0; i--) {
            Message m = tail[i];
            Address[] fromAddr = m.getFrom();
            String fromStr = (fromAddr != null && fromAddr.length > 0) ? fromAddr[0].toString() : "";
            if (fromStr.contains(senderEmail)) {
                return m;
            }
        }
        return null;
    }

    // === 3) Извлечение первого вложения в temp‑файл ===
    private Attachment extractFirstAttachment(Message message) throws Exception {
        Object content = message.getContent();
        if (!(content instanceof Multipart multipart)) {
            System.out.println("⚠ Не multipart письмо — вложений нет");
            return null;
        }

        for (int j = 0; j < multipart.getCount(); j++) {
            BodyPart part = multipart.getBodyPart(j);

            boolean looksLikeAttachment =
                    Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())
                            || part.getFileName() != null;

            if (!looksLikeAttachment) continue;

            String rawName = part.getFileName();
            String decodedName = MimeUtility.decodeText(rawName != null ? rawName : "attachment.bin");
            String safeName = decodedName.replaceAll("[^a-zA-Z0-9\\.\\-_]", "_");

            File tempFile = File.createTempFile("mail_attach_", "_" + safeName);
            try (InputStream is = part.getInputStream();
                 OutputStream os = new FileOutputStream(tempFile)) {
                is.transferTo(os);
            }
            return new Attachment(decodedName, tempFile);
        }
        return null;
    }

    // === вспомогательные типы для try‑with‑resources и данных вложения ===
    private static final class StoreResource implements AutoCloseable {
        private final Store store;
        StoreResource(Store store) { this.store = store; }
        Store get() { return store; }
        @Override public void close() {
            try { if (store != null && store.isConnected()) store.close(); } catch (MessagingException ignored) {}
        }
    }
    private static final class FolderResource implements AutoCloseable {
        private final Folder folder;
        FolderResource(Folder folder) { this.folder = folder; }
        Folder get() { return folder; }
        @Override public void close() {
            try { if (folder != null && folder.isOpen()) folder.close(false); } catch (MessagingException ignored) {}
        }
    }
    private static final class Attachment {
        final String originalName;
        final File file;
        Attachment(String originalName, File file) { this.originalName = originalName; this.file = file; }
    }
}



//    private void disableSslVerification() {
//        try {
//            TrustManager[] trustAllCerts = new TrustManager[]{
//                    new X509TrustManager() {
//                        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
//                        public void checkClientTrusted(X509Certificate[] certs, String authType) {}
//                        public void checkServerTrusted(X509Certificate[] certs, String authType) {}
//                    }
//            };
//            SSLContext sc = SSLContext.getInstance("TLS");
//            sc.init(null, trustAllCerts, new java.security.SecureRandom());
//            SSLContext.setDefault(sc);
//            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
//            HttpsURLConnection.setDefaultHostnameVerifier((h, s) -> true);
//        } catch (Exception ignored) {}
//    }
