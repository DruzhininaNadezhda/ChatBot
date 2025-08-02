package com.example.chatbot.logger;

import java.util.Timer;
import java.util.TimerTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MemoryLogger {

    private static final Logger log = LoggerFactory.getLogger(MemoryLogger.class);

    public MemoryLogger() {
        Timer timer = new Timer(true); // daemon
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                long total = Runtime.getRuntime().totalMemory() / 1024 / 1024;
                long free = Runtime.getRuntime().freeMemory() / 1024 / 1024;
                long used = total - free;

                log.info("🧠 MEMORY USAGE: used={}MB, free={}MB, total={}MB", used, free, total);
            }
        }, 0, 10 * 60 * 1000); // каждые 5 минут
    }
}
