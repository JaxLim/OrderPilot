package com.orderpilot.payment;

import com.orderpilot.common.api.ApiResponse;
import com.orderpilot.common.api.PageQuery;
import com.orderpilot.common.api.PageResult;
import com.orderpilot.payment.PaymentService.PayRequest;
import com.orderpilot.payment.PaymentService.RefundRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Payments")
@RestController
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/api/orders/{orderId}/payments")
    @PreAuthorize("hasAuthority('payment:write')")
    public ApiResponse<Payment> pay(@PathVariable long orderId, @Valid @RequestBody PayRequest req,
                                    @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return ApiResponse.ok(paymentService.pay(orderId, req, key));
    }

    @PostMapping("/api/orders/{orderId}/refund")
    @PreAuthorize("hasAuthority('payment:refund')")
    public ApiResponse<Payment> refund(@PathVariable long orderId, @Valid @RequestBody RefundRequest req) {
        return ApiResponse.ok(paymentService.refund(orderId, req.reason()));
    }

    @GetMapping("/api/orders/{orderId}/payments")
    @PreAuthorize("hasAuthority('order:read')")
    public ApiResponse<List<Payment>> forOrder(@PathVariable long orderId) {
        return ApiResponse.ok(paymentService.forOrder(orderId));
    }

    @GetMapping("/api/payments")
    @PreAuthorize("hasAuthority('order:read')")
    public ApiResponse<PageResult<Payment>> list(@RequestParam(required = false) String status,
                                                 @RequestParam(required = false) Integer page,
                                                 @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(paymentService.page(status, PageQuery.of(page, size)));
    }
}
