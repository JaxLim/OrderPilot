package com.orderpilot.ai;

import com.orderpilot.analytics.AnalyticsMapper;
import com.orderpilot.catalog.ProductService;
import com.orderpilot.common.error.BizException;
import com.orderpilot.common.error.ErrorCode;
import com.orderpilot.common.tenant.TenantContext;
import com.orderpilot.iam.AuthUser;
import com.orderpilot.iam.domain.Tenant;
import com.orderpilot.iam.domain.TenantMapper;
import com.orderpilot.inventory.InventoryService;
import com.orderpilot.order.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);
    private static final int MAX_REQUESTS_PER_MINUTE = 20;

    public record ChatTurn(@NotBlank @Pattern(regexp = "user|assistant") String role,
                           @NotBlank @Size(max = 8000) String content) {
    }

    public record ChatRequest(@NotBlank @Size(max = 2000) String message, @Size(max = 20) List<@Valid ChatTurn> history) {
    }

    public record ChatReply(String reply, String mode, List<String> toolsUsed, String notice) {
    }

    private static final String SYSTEM_PROMPT = """
            You are OrderPilot Copilot, a concise business analyst for the company "%s".
            Today is %s. Currency is USD.
            Rules:
            - Use the provided tools for every figure you state. Never guess or invent numbers.
            - You are read-only: you cannot create, change or delete orders, products or stock. If asked, explain
              which screen in OrderPilot the user can use instead.
            - Only discuss this company's business data and operations; politely decline unrelated requests.
            - Answer in GitHub-flavoured Markdown. Prefer short paragraphs, bullet points and small tables.
            - When useful, end with one or two concrete, data-backed recommendations.
            """;

    private final ObjectProvider<ChatClient.Builder> chatClientBuilder;
    private final boolean llmConfigured;
    private final AnalyticsMapper analytics;
    private final InventoryService inventoryService;
    private final OrderService orderService;
    private final ProductService productService;
    private final TenantMapper tenantMapper;
    private final StringRedisTemplate redis;
    private final OfflineAnalyst offline = new OfflineAnalyst();

    public AssistantService(ObjectProvider<ChatClient.Builder> chatClientBuilder,
                            @Value("${orderpilot.ai.api-key:}") String apiKey,
                            AnalyticsMapper analytics, InventoryService inventoryService, OrderService orderService,
                            ProductService productService, TenantMapper tenantMapper, StringRedisTemplate redis) {
        this.chatClientBuilder = chatClientBuilder;
        this.llmConfigured = apiKey != null && !apiKey.isBlank();
        this.analytics = analytics;
        this.inventoryService = inventoryService;
        this.orderService = orderService;
        this.productService = productService;
        this.tenantMapper = tenantMapper;
        this.redis = redis;
        log.info("AI assistant mode: {}", llmConfigured ? "LLM (Spring AI, OpenAI-compatible)" : "offline analyst");
    }

    public boolean llmConfigured() {
        return llmConfigured;
    }

    public ChatReply chat(ChatRequest req) {
        AuthUser user = AuthUser.current();
        throttle(user);
        long tenantId = TenantContext.require();
        BusinessTools tools = new BusinessTools(tenantId, analytics, inventoryService, orderService, productService);

        if (llmConfigured) {
            try {
                String reply = callLlm(req, tools, tenantMapper.findById(tenantId));
                return new ChatReply(reply, "llm", tools.invokedTools(), null);
            } catch (RuntimeException e) {
                log.warn("LLM call failed, falling back to offline analyst: {}", e.toString());
                BusinessTools fallbackTools = new BusinessTools(tenantId, analytics, inventoryService, orderService,
                        productService);
                return new ChatReply(offline.answer(req.message(), fallbackTools), "offline",
                        fallbackTools.invokedTools(), "The AI provider is unavailable; answered with the built-in analyst.");
            }
        }
        return new ChatReply(offline.answer(req.message(), tools), "offline", tools.invokedTools(), null);
    }

    private String callLlm(ChatRequest req, BusinessTools tools, Tenant tenant) {
        ChatClient client = chatClientBuilder.getObject().build();
        List<Message> history = new ArrayList<>();
        if (req.history() != null) {
            for (ChatTurn t : req.history()) {
                history.add("user".equals(t.role()) ? new UserMessage(t.content()) : new AssistantMessage(t.content()));
            }
        }
        return client.prompt()
                .system(SYSTEM_PROMPT.formatted(tenant.getName(), LocalDate.now()))
                .messages(history)
                .user(req.message())
                .tools(tools)
                .call()
                .content();
    }

    /** Per-user fixed-window limit: LLM calls cost money. */
    private void throttle(AuthUser user) {
        String key = "op:ratelimit:ai:" + user.userId();
        try {
            Long n = redis.opsForValue().increment(key);
            if (n != null && n == 1L) {
                redis.expire(key, Duration.ofMinutes(1));
            }
            if (n != null && n > MAX_REQUESTS_PER_MINUTE) {
                throw new BizException(ErrorCode.RATE_LIMITED, "Too many assistant requests, please wait a minute");
            }
        } catch (DataAccessException e) {
            log.warn("Redis unavailable for AI rate limiting: {}", e.getMessage());
        }
    }
}
