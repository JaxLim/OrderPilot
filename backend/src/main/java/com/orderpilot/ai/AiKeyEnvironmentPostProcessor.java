package com.orderpilot.ai;

import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Decides once, at startup, whether the LLM is available. The key itself ({@code AI_API_KEY}) only
 * ever comes from the process environment or a git-ignored {@code .env}; this class never logs it.
 *
 * <p>Spring AI refuses to start with a blank key, and Docker Compose passes an unset variable as an
 * empty string, so a blank key is replaced by a placeholder and the assistant runs in offline mode.
 */
public class AiKeyEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String ENABLED = "orderpilot.ai.enabled";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication application) {
        String key = env.getProperty("AI_API_KEY");
        boolean configured = key != null && !key.isBlank();
        Map<String, Object> props = configured
                ? Map.of(ENABLED, true)
                : Map.of(ENABLED, false, "spring.ai.openai.api-key", "not-configured");
        env.getPropertySources().addFirst(new MapPropertySource("orderpilotAiKey", props));
    }

    @Override
    public int getOrder() {
        // after ConfigDataEnvironmentPostProcessor, so values imported from .env are visible
        return Ordered.LOWEST_PRECEDENCE;
    }
}
