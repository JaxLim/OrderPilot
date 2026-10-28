package com.orderpilot.ai;

import com.orderpilot.ai.AssistantService.ChatReply;
import com.orderpilot.ai.AssistantService.ChatRequest;
import com.orderpilot.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI Assistant")
@RestController
@RequestMapping("/api/ai")
@PreAuthorize("hasAuthority('ai:use')")
public class AssistantController {

    private final AssistantService assistantService;

    public AssistantController(AssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.ok(Map.of("mode", assistantService.llmConfigured() ? "llm" : "offline"));
    }

    @PostMapping("/chat")
    public ApiResponse<ChatReply> chat(@Valid @RequestBody ChatRequest req) {
        return ApiResponse.ok(assistantService.chat(req));
    }
}
