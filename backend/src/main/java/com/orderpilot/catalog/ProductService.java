package com.orderpilot.catalog;

import com.orderpilot.audit.AuditService;
import com.orderpilot.catalog.ProductDtos.CreateProductRequest;
import com.orderpilot.catalog.ProductDtos.ProductView;
import com.orderpilot.catalog.ProductDtos.UpdateProductRequest;
import com.orderpilot.common.api.PageQuery;
import com.orderpilot.common.api.PageResult;
import com.orderpilot.common.error.BizException;
import com.orderpilot.common.error.ErrorCode;
import com.orderpilot.common.tenant.TenantContext;
import com.orderpilot.inventory.InventoryService;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final ProductMapper productMapper;
    private final ProductCache productCache;
    private final InventoryService inventoryService;
    private final AuditService auditService;

    public ProductService(ProductMapper productMapper, ProductCache productCache, InventoryService inventoryService,
                          AuditService auditService) {
        this.productMapper = productMapper;
        this.productCache = productCache;
        this.inventoryService = inventoryService;
        this.auditService = auditService;
    }

    public PageResult<ProductView> search(String keyword, String category, String status, PageQuery q) {
        long tenantId = TenantContext.require();
        List<ProductView> items = productMapper.search(tenantId, keyword, category, status, q.size(), q.offset());
        long total = productMapper.count(tenantId, keyword, category, status);
        return new PageResult<>(items, total, q.page(), q.size());
    }

    public ProductView detail(long id) {
        ProductView view = productMapper.findView(TenantContext.require(), id);
        if (view == null) {
            throw BizException.notFound("Product", id);
        }
        return view;
    }

    /** Cached catalog lookup used by the order path. */
    public Optional<Product> findCached(long id) {
        long tenantId = TenantContext.require();
        return productCache.get(tenantId, id, () -> productMapper.findById(tenantId, id));
    }

    public List<String> categories() {
        return productMapper.categories(TenantContext.require());
    }

    @Transactional
    public ProductView create(CreateProductRequest req) {
        long tenantId = TenantContext.require();
        if (productMapper.findBySku(tenantId, req.sku()) != null) {
            throw new BizException(ErrorCode.CONFLICT, "SKU already exists: " + req.sku());
        }
        Product p = new Product();
        p.setTenantId(tenantId);
        p.setSku(req.sku());
        p.setName(req.name());
        p.setCategory(req.category());
        p.setDescription(req.description());
        p.setPrice(req.price());
        p.setCost(req.cost());
        p.setLowStockThreshold(req.lowStockThreshold());
        p.setStatus("ACTIVE");
        productMapper.insert(p);
        inventoryService.initialize(p.getId(), req.initialStock());
        productCache.evictAfterCommit(tenantId, p.getId()); // clears a cached "not found" sentinel
        auditService.record("product.create", "product", String.valueOf(p.getId()), p.getSku());
        return detailOf(tenantId, p.getId());
    }

    @Transactional
    public ProductView update(long id, UpdateProductRequest req) {
        long tenantId = TenantContext.require();
        Product p = require(tenantId, id);
        p.setName(req.name());
        p.setCategory(req.category());
        p.setDescription(req.description());
        p.setPrice(req.price());
        p.setCost(req.cost());
        p.setLowStockThreshold(req.lowStockThreshold());
        productMapper.update(p);
        productCache.evictAfterCommit(tenantId, id);
        auditService.record("product.update", "product", String.valueOf(id), p.getSku() + " price=" + req.price());
        return detailOf(tenantId, id);
    }

    @Transactional
    public ProductView changeStatus(long id, String status) {
        long tenantId = TenantContext.require();
        Product p = require(tenantId, id);
        productMapper.updateStatus(tenantId, id, status);
        productCache.evictAfterCommit(tenantId, id);
        auditService.record("product." + status.toLowerCase(), "product", String.valueOf(id), p.getSku());
        return detailOf(tenantId, id);
    }

    private Product require(long tenantId, long id) {
        Product p = productMapper.findById(tenantId, id);
        if (p == null) {
            throw BizException.notFound("Product", id);
        }
        return p;
    }

    private ProductView detailOf(long tenantId, long id) {
        return productMapper.findView(tenantId, id);
    }
}
