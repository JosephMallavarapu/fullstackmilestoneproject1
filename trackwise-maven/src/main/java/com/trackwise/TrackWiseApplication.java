package com.trackwise;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * TrackWise — Relational Expense Analytics Platform
 *
 * Eclipse Maven Project Entry Point
 * Run: mvn spring-boot:run
 * API Docs: http://localhost:8080/api/v1/swagger-ui.html
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
@OpenAPIDefinition(info = @Info(title = "TrackWise API", version = "1.0.0", description = "Relational Expense Analytics Platform — REST API"))
public class TrackWiseApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrackWiseApplication.class, args);
    }
}
