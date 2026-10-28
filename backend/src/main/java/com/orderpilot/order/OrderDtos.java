package com.orderpilot.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class OrderDtos {

    private OrderDtos() {
    }

    public record CreateOrderItem(@NotNull Long productId, @NotNull @Min(1) @Max(10000) Integer quantity) {
    }

    public record CreateOrderRequest(
            @NotBlank @Size(max = 120) String customerName,
            @Email @Size(max = 160) String customerEmail,
            @Size(max = 255) String shippingAddress,
            @Size(max = 255) String remark,
            @NotEmpty @Size(max = 50) List<@Valid CreateOrderItem> items) {
    }

    public record CancelRequest(@Size(max = 255) String reason) {
    }

    public record OrderDetail(SalesOrder order, List<OrderItem> items) {
    }
}
