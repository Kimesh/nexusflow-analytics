package com.nexusflow.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CoreGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoreGatewayApplication.class, args);
    }
}
