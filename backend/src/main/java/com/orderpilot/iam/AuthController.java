package com.orderpilot.iam;

import com.orderpilot.common.api.ApiResponse;
import com.orderpilot.iam.AuthDtos.LoginRequest;
import com.orderpilot.iam.AuthDtos.LoginResponse;
import com.orderpilot.iam.AuthDtos.Me;
import com.orderpilot.iam.AuthDtos.RegisterRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        return ApiResponse.ok(authService.login(req));
    }

    /** Self-service sign-up: creates a new tenant workspace and its first admin. */
    @PostMapping("/register")
    public ApiResponse<LoginResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ApiResponse.ok(authService.register(req));
    }

    @GetMapping("/me")
    public ApiResponse<Me> me() {
        return ApiResponse.ok(authService.me(AuthUser.current()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        authService.logout((JwtService.ParsedToken) request.getAttribute(JwtAuthenticationFilter.TOKEN_ATTR));
        return ApiResponse.ok();
    }
}
