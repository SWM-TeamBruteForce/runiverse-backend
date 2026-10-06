package com.runiverse.running_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RunningServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RunningServiceApplication.class, args);
    }

}
