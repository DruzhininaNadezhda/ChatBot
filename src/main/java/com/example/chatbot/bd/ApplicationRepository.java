package com.example.chatbot.bd;

import com.example.chatbot.dto.ApplicationDTO;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class ApplicationRepository {

    private final Map<String, ApplicationDTO> active = new ConcurrentHashMap<>();
    private final Map<String, ApplicationDTO> finished = new ConcurrentHashMap<>();

    public ApplicationDTO getOrCreate(String applicationId) {
        return active.computeIfAbsent(applicationId, ApplicationDTO::new);
    }
    public void setUsername(String applicationId, String username) {
        ApplicationDTO dto = active.get(applicationId);
        if (dto != null && username != null && !username.isBlank()) {
            dto.setUsername(username.trim());
        }
    }
    public void addComment(String applicationId, String comment) {
        ApplicationDTO dto = getOrCreate(applicationId);
        dto.addComment(comment);
    }

    public ApplicationDTO get(String applicationId) {
        return active.get(applicationId);
    }

    public void save(String applicationId, ApplicationDTO dto) {
        active.put(applicationId, dto);
    }

    public void clear(String applicationId) {
        active.put(applicationId, new ApplicationDTO(applicationId));
    }

    public boolean isReady(String applicationId) {
        ApplicationDTO dto = active.get(applicationId);
        return dto != null &&
                notEmpty(dto.getUsername()) &&
                notEmpty(dto.getDds()) &&
                notEmpty(dto.getReceiver()) &&
                notEmpty(dto.getContact()) &&
                notEmpty(dto.getPaymentDate()) &&
                dto.getFile() != null;
    }

    public boolean finalizeApplication(String applicationId) {
        ApplicationDTO dto = active.remove(applicationId);
        if (dto == null) return false;
        finished.put(applicationId, dto);
        return true;
    }

    private boolean notEmpty(String s) {
        return s != null && !s.isBlank();
    }

    // Можно добавить методы: findAllFinished(), countActive(), clearAll() и т.д.
}

