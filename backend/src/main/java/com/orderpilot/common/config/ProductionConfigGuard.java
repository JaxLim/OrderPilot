package com.orderpilot.common.config;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;

/**
 * Stops the application before anything connects or migrates when the {@code prod} profile runs
 * with configuration that is public in this repository. Messages name the setting, never its value.
 */
public class ProductionConfigGuard implements EnvironmentPostProcessor, Ordered {

    private static final int MIN_SECRET_BYTES = 32;
    private static final List<String> PLACEHOLDERS = List.of("change-me", "replace-with", "docker-compose-demo-secret");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication application) {
        if (!env.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }
        List<String> problems = problems(env);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Refusing to start with the prod profile: " + String.join("; ", problems));
        }
    }

    private static List<String> problems(ConfigurableEnvironment env) {
        List<String> problems = new ArrayList<>();
        String secret = env.getProperty("orderpilot.jwt.secret", "");
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            problems.add("JWT_SECRET must be set to at least " + MIN_SECRET_BYTES + " bytes");
        } else if (PLACEHOLDERS.stream().anyMatch(secret.toLowerCase(Locale.ROOT)::contains)) {
            problems.add("JWT_SECRET still holds a placeholder value");
        }
        if (env.acceptsProfiles(Profiles.of("demo"))) {
            problems.add("the demo profile cannot run with prod");
        }
        if (env.getProperty("orderpilot.demo.seed", Boolean.class, false)) {
            problems.add("DEMO_SEED must be false");
        }
        if (List.of("", "orderpilot").contains(env.getProperty("spring.datasource.password", ""))) {
            problems.add("MYSQL_PASSWORD must not be blank or the default");
        }
        if (List.of("", "guest").contains(env.getProperty("spring.rabbitmq.password", ""))) {
            problems.add("RABBITMQ_PASSWORD must not be blank or the default");
        }
        return problems;
    }

    @Override
    public int getOrder() {
        // after ConfigDataEnvironmentPostProcessor, so profiles and values imported from .env are visible
        return Ordered.LOWEST_PRECEDENCE;
    }
}
