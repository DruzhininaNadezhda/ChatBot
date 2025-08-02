package com.example.chatbot.conf;

import com.example.chatbot.dto.ApplicationDTO;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.annotation.SessionScope;

@Configuration
public class DtoConfig {
    @Bean
    @SessionScope
    public ApplicationDTO applicationRequestDTO() {
        return new ApplicationDTO();
    }
}
