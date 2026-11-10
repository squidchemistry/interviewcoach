package com.aicoach;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AiCoachApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiCoachApplication.class, args);
    }
}
