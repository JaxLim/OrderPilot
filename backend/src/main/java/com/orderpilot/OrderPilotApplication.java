package com.orderpilot;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class OrderPilotApplication {

    public static void main(String[] args) {
        // All timestamps are stored and computed in UTC; the UI converts to the viewer's zone.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        // Spring AI rejects a blank key at startup; an empty OPENAI_API_KEY (e.g. from Compose) means "offline mode".
        String key = System.getenv("OPENAI_API_KEY");
        if (key == null || key.isBlank()) {
            System.setProperty("spring.ai.openai.api-key", "not-configured");
        }
        SpringApplication.run(OrderPilotApplication.class, args);
    }
}
