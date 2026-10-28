package com.orderpilot.catalog;

import com.orderpilot.catalog.ProductDtos.CreateProductRequest;
import com.orderpilot.catalog.ProductDtos.ProductView;
import com.orderpilot.catalog.ProductDtos.UpdateProductRequest;
import com.orderpilot.common.api.ApiResponse;
import com.orderpilot.common.api.PageQuery;
import com.orderpilot.common.api.PageResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Products")
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('product:read')")
    public ApiResponse<PageResult<ProductView>> search(@RequestParam(required = false) String keyword,
                                                       @RequestParam(required = false) String category,
                                                       @RequestParam(required = false) String status,
                                                       @RequestParam(required = false) Integer page,
                                                       @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(productService.search(keyword, category, status, PageQuery.of(page, size)));
    }

    @GetMapping("/categories")
    @PreAuthorize("hasAuthority('product:read')")
    public ApiResponse<List<String>> categories() {
        return ApiResponse.ok(productService.categories());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('product:read')")
    public ApiResponse<ProductView> detail(@PathVariable long id) {
        return ApiResponse.ok(productService.detail(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('product:write')")
    public ApiResponse<ProductView> create(@Valid @RequestBody CreateProductRequest req) {
        return ApiResponse.ok(productService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('product:write')")
    public ApiResponse<ProductView> update(@PathVariable long id, @Valid @RequestBody UpdateProductRequest req) {
        return ApiResponse.ok(productService.update(id, req));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAuthority('product:write')")
    public ApiResponse<ProductView> archive(@PathVariable long id) {
        return ApiResponse.ok(productService.changeStatus(id, "ARCHIVED"));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('product:write')")
    public ApiResponse<ProductView> activate(@PathVariable long id) {
        return ApiResponse.ok(productService.changeStatus(id, "ACTIVE"));
    }
}
