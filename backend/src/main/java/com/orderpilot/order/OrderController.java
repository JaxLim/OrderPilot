package com.orderpilot.order;

import com.orderpilot.common.api.ApiResponse;
import com.orderpilot.common.api.PageQuery;
import com.orderpilot.common.api.PageResult;
import com.orderpilot.order.OrderDtos.CancelRequest;
import com.orderpilot.order.OrderDtos.CreateOrderRequest;
import com.orderpilot.order.OrderDtos.OrderDetail;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Orders")
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('order:read')")
    public ApiResponse<PageResult<SalesOrder>> search(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(orderService.search(status, keyword, from, to, PageQuery.of(page, size)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('order:read')")
    public ApiResponse<OrderDetail> detail(@PathVariable long id) {
        return ApiResponse.ok(orderService.detail(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('order:write')")
    public ApiResponse<OrderDetail> create(@Valid @RequestBody CreateOrderRequest req,
                                           @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return ApiResponse.ok(orderService.create(req, key));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('order:write')")
    public ApiResponse<OrderDetail> cancel(@PathVariable long id, @Valid @RequestBody(required = false) CancelRequest req) {
        return ApiResponse.ok(orderService.cancel(id, req == null ? null : req.reason()));
    }

    @PostMapping("/{id}/ship")
    @PreAuthorize("hasAuthority('order:fulfil')")
    public ApiResponse<OrderDetail> ship(@PathVariable long id) {
        return ApiResponse.ok(orderService.ship(id));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('order:fulfil')")
    public ApiResponse<OrderDetail> complete(@PathVariable long id) {
        return ApiResponse.ok(orderService.complete(id));
    }
}
