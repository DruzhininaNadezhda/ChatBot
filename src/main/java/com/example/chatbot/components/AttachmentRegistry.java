package com.example.chatbot.components;

import org.springframework.stereotype.Component;

import java.io.File;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AttachmentRegistry {

    public static final class Item {
        public final File file;
        public final String originalName;
        public final String mimeType;
        public final long size;
        public final Instant expiresAt;

        public Item(File file, String originalName, String mimeType, long size, Instant expiresAt) {
            this.file = file;
            this.originalName = originalName;
            this.mimeType = mimeType;
            this.size = size;
            this.expiresAt = expiresAt;
        }
    }

    private final Map<String, Item> storage = new ConcurrentHashMap<>();

    /** Регистрируем временный файл и получаем токен. TTL — 15 минут. */
    public String register(File file, String originalName, String mimeType, long size) {
        String token = UUID.randomUUID().toString();
        storage.put(token, new Item(file, originalName, mimeType, size, Instant.now().plusSeconds(15 * 60)));
        return token;
    }

    /** Достаём и одновременно удаляем, если просрочен. */
    public Item get(String token) {
        Item item = storage.get(token);
        if (item == null) return null;
        if (Instant.now().isAfter(item.expiresAt)) {
            remove(token);
            return null;
        }
        return item;
    }

    public void remove(String token) {
        Item item = storage.remove(token);
        if (item != null && item.file != null && item.file.exists()) {
            // не удаляем сразу: файл может понадобиться повторно показать (если оставишь — ок)
            // Если хочешь удалять сразу — раскомментируй:
            // item.file.delete();
        }
    }
}

