package com.naag.rag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableCaching
@EnableScheduling
public class NaagRagServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(NaagRagServiceApplication.class, args);
    }
}
