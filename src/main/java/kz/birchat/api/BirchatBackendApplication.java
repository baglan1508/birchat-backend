package kz.birchat.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BirchatBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BirchatBackendApplication.class, args);
    }
}