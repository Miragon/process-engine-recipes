package io.miragon.miravelo.workshop;

import org.cibseven.bpm.spring.boot.starter.annotation.EnableProcessApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableProcessApplication
public class WorkshopApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkshopApplication.class, args);
    }
}
