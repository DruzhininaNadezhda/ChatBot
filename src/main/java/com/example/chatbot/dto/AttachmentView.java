package com.example.chatbot.dto;

import lombok.*;
import org.springframework.stereotype.Component;

@Data
@NoArgsConstructor
@Getter
@Setter
@AllArgsConstructor
@Builder
public class AttachmentView {
    private String name;
    private String mimeType;
    private long size;
    private String url;
}
