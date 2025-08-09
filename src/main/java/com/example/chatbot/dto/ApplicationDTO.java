package com.example.chatbot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import org.springframework.web.context.annotation.SessionScope;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@Getter
@Setter
@AllArgsConstructor
@Builder
public class ApplicationDTO implements Serializable {
    private String username;
    private String userEmail;
    private String bitrixUserId;
    private List<String> comments = new ArrayList<>();
    private String applicationId;
    @JsonProperty(value = "статья_ддс")
    private String dds;
    @JsonProperty(value = "получатель")
    private String receiver;
    @JsonProperty(value = "контактное_лицо")
    private String contact;
    @JsonProperty(value = "дата_платежа")
    private String paymentDate;
    @JsonProperty(value = "дата_поступления")
    private String deliveryDate;
    private File file;
    private String originalFileName;
    public ApplicationDTO(String applicationId) {
        this.applicationId = applicationId;
    }
    public void addComment(String comment) {
        if (comments == null) {
            comments = new ArrayList<>();
        }
        comments.add(comment);
    }

}
