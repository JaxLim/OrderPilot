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

    public record ChatRequest(@NotBlank @Size(max = 2000) String message, @Size(max = 20) List<@Valid ChatTurn> history,
                              @Pattern(regexp = "en|zh") String lang) {
        public boolean chinese() {
            return "zh".equals(lang);
        }
    }

    public record ChatReply(String reply, String mode, List<String> toolsUsed, String notice) {
    }

    /** What the UI may know about the AI setup. Deliberately excludes the API key. */
    public record AiStatus(String mode, String provider, String model) {
    }

    private static final java.util.regex.Pattern SECRET = java.util.regex.Pattern.compile("sk-[A-Za-z0-9_-]{6,}");

    private static final String SYSTEM_PROMPT = """
            You are OrderPilot Copilot, a concise business analyst for the company "%s".
            Today is %s. Currency is USD.
            Rules:
            - Use the provided tools for every figure you state. Never guess or invent numbers.
            - You are read-only: you cannot create, change or delete orders, products or stock. If asked, point the user
              to the real OrderPilot screen. The screens are: Dashboard; Orders (create, pay, cancel, ship, refund);
              Payments; Products (create, edit, archive); Inventory (stock levels with a "low stock only" filter, an
              Adjust button to record goods received or corrections, and the movement ledger); Team; Activity.
              Never mention features that are not in this list (there are no purchase orders or suppliers).
            - Only discuss this company's business data and operations; politely decline unrelated requests.
            - Answer in GitHub-flavoured Markdown. Prefer short paragraphs, bullet points and small tables.
            - When useful, end with one or two concrete, data-backed recommendations.
            - %s
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
    private final String provider;
    private final String model;

    public AssistantService(ObjectProvider<ChatClient.Builder> chatClientBuilder,
                            @Value("${orderpilot.ai.enabled:false}") boolean llmConfigured,
                            @Value("${orderpilot.ai.provider:DeepSeek}") String provider,
                            @Value("${orderpilot.ai.model:deepseek-chat}") String model,
                            AnalyticsMapper analytics, InventoryService inventoryService, OrderService orderService,
                            ProductService productService, TenantMapper tenantMapper, StringRedisTemplate redis) {
        this.chatClientBuilder = chatClientBuilder;
        this.llmConfigured = llmConfigured;
        this.provider = provider;
        this.model = model;
        this.analytics = analytics;
        this.inventoryService = inventoryService;
        this.orderService = orderService;
        this.productService = productService;
        this.tenantMapper = tenantMapper;
        this.redis = redis;
        log.info("AI assistant mode: {}", llmConfigured ? "LLM via " + provider + " (" + model + ")" : "offline analyst");
    }

    public AiStatus status() {
        return llmConfigured ? new AiStatus("llm", provider, model) : new AiStatus("offline", null, null);
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
                log.warn("LLM call to {} failed, falling back to offline analyst: {}", provider, redact(e));
                BusinessTools fallbackTools = new BusinessTools(tenantId, analytics, inventoryService, orderService,
                        productService);
                String notice = req.chinese() ? "AI 服务暂时不可用，已由内置分析助手回答。"
                        : "The AI provider is unavailable; answered with the built-in analyst.";
                return new ChatReply(offline.answer(req.message(), fallbackTools, req.chinese()), "offline",
                        fallbackTools.invokedTools(), notice);
            }
        }
        return new ChatReply(offline.answer(req.message(), tools, req.chinese()), "offline", tools.invokedTools(), null);
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
                .system(SYSTEM_PROMPT.formatted(tenant.getName(), LocalDate.now(), req.chinese()
                        ? "Always reply in Simplified Chinese (简体中文). Keep SKUs, order numbers and product names as they are."
                        : "Reply in English unless the user writes in another language."))
                .messages(history)
                .user(req.message())
                .tools(tools)
                .call()
                .content();
    }

    /** Error text for logs: exception type plus message with anything that looks like an API key masked. */
    static String redact(Throwable e) {
        String msg = e.getClass().getSimpleName() + ": " + e.getMessage();
        msg = SECRET.matcher(msg).replaceAll("sk-***");
        return msg.length() > 300 ? msg.substring(0, 300) + "…" : msg;
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
