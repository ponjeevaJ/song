package io.isaiworld;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class IsaiWorldApplication {
    public static void main(String[] args) {
        SpringApplication.run(IsaiWorldApplication.class, args);
    }
}
