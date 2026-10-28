package com.orderpilot.inventory;

import com.orderpilot.common.api.ApiResponse;
import com.orderpilot.common.api.PageQuery;
import com.orderpilot.common.api.PageResult;
import com.orderpilot.inventory.InventoryDtos.AdjustRequest;
import com.orderpilot.inventory.InventoryDtos.InventoryRow;
import com.orderpilot.inventory.InventoryDtos.Movement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Inventory")
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('inventory:read')")
    public ApiResponse<PageResult<InventoryRow>> list(@RequestParam(required = false) String keyword,
                                                      @RequestParam(defaultValue = "false") boolean lowOnly,
                                                      @RequestParam(required = false) Integer page,
                                                      @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(inventoryService.list(keyword, lowOnly, PageQuery.of(page, size)));
    }

    @GetMapping("/{productId}")
    @PreAuthorize("hasAuthority('inventory:read')")
    public ApiResponse<InventoryRow> get(@PathVariable long productId) {
        return ApiResponse.ok(inventoryService.get(productId));
    }

    @PostMapping("/{productId}/adjustments")
    @PreAuthorize("hasAuthority('inventory:write')")
    public ApiResponse<InventoryRow> adjust(@PathVariable long productId, @Valid @RequestBody AdjustRequest req) {
        return ApiResponse.ok(inventoryService.adjust(productId, req));
    }

    @GetMapping("/movements")
    @PreAuthorize("hasAuthority('inventory:read')")
    public ApiResponse<PageResult<Movement>> movements(@RequestParam(required = false) Long productId,
                                                       @RequestParam(required = false) Integer page,
                                                       @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(inventoryService.movements(productId, PageQuery.of(page, size)));
    }
}
