package com.smartlocator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

@SpringBootApplication
public class SmartLocatorApplication {

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("🎯 Smart Locator Capture Tool");
        System.out.println("========================================");
        System.out.println("Starting application...");
        
        SpringApplication.run(SmartLocatorApplication.class, args);
        
        System.out.println("========================================");
        System.out.println("✅ Application started successfully!");
        System.out.println("🌐 Open your browser to: http://localhost:8080");
        System.out.println("========================================");
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        return mapper;
    }
}
