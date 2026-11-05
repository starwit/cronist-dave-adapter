package de.starwit.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Main SpringApplication to start the whole project. */
@SpringBootApplication(scanBasePackages = "de.starwit")
@ConfigurationPropertiesScan("de.starwit.config")
@EnableScheduling
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
