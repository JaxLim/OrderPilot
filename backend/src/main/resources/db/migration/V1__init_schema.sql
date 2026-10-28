-- OrderPilot schema. Shared-schema multi-tenancy: every business row carries tenant_id,
-- and every tenant-scoped unique key is (tenant_id, ...).

CREATE TABLE tenant (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code        VARCHAR(40)  NOT NULL,
    name        VARCHAR(120) NOT NULL,
    plan        VARCHAR(20)  NOT NULL DEFAULT 'STARTER',
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_tenant_code (code)
) ENGINE = InnoDB;

CREATE TABLE app_user (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id      BIGINT       NOT NULL,
    email          VARCHAR(160) NOT NULL,
    password_hash  VARCHAR(100) NOT NULL,
    display_name   VARCHAR(80)  NOT NULL,
    role           VARCHAR(20)  NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    last_login_at  DATETIME(3)  NULL,
    created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_user_email (email),
    KEY idx_user_tenant (tenant_id)
) ENGINE = InnoDB;

CREATE TABLE product (
    id                   BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id            BIGINT         NOT NULL,
    sku                  VARCHAR(64)    NOT NULL,
    name                 VARCHAR(160)   NOT NULL,
    category             VARCHAR(60)    NOT NULL,
    description          VARCHAR(1000)  NULL,
    price                DECIMAL(12, 2) NOT NULL,
    cost                 DECIMAL(12, 2) NOT NULL DEFAULT 0,
    low_stock_threshold  INT            NOT NULL DEFAULT 10,
    status               VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_at           DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at           DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_product_sku (tenant_id, sku),
    KEY idx_product_tenant_cat (tenant_id, category)
) ENGINE = InnoDB;

-- available: sellable now; reserved: held by unpaid orders. On payment, reserved is deducted.
CREATE TABLE inventory (
    id          BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id   BIGINT      NOT NULL,
    product_id  BIGINT      NOT NULL,
    available   INT         NOT NULL DEFAULT 0,
    reserved    INT         NOT NULL DEFAULT 0,
    version     BIGINT      NOT NULL DEFAULT 0,
    updated_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_inventory_product (tenant_id, product_id),
    CONSTRAINT chk_inventory_available CHECK (available >= 0),
    CONSTRAINT chk_inventory_reserved CHECK (reserved >= 0)
) ENGINE = InnoDB;

CREATE TABLE inventory_movement (
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id    BIGINT       NOT NULL,
    product_id   BIGINT       NOT NULL,
    type         VARCHAR(20)  NOT NULL,
    quantity     INT          NOT NULL,
    ref_type     VARCHAR(20)  NULL,
    ref_id       VARCHAR(64)  NULL,
    note         VARCHAR(255) NULL,
    operator_id  BIGINT       NULL,
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_movement_product (tenant_id, product_id, created_at),
    KEY idx_movement_tenant (tenant_id, created_at)
) ENGINE = InnoDB;

CREATE TABLE sales_order (
    id                BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id         BIGINT         NOT NULL,
    order_no          VARCHAR(32)    NOT NULL,
    customer_name     VARCHAR(120)   NOT NULL,
    customer_email    VARCHAR(160)   NULL,
    shipping_address  VARCHAR(255)   NULL,
    status            VARCHAR(20)    NOT NULL,
    total_amount      DECIMAL(14, 2) NOT NULL,
    item_count        INT            NOT NULL,
    idempotency_key   VARCHAR(64)    NULL,
    remark            VARCHAR(255)   NULL,
    created_by        BIGINT         NULL,
    expires_at        DATETIME(3)    NULL,
    paid_at           DATETIME(3)    NULL,
    shipped_at        DATETIME(3)    NULL,
    completed_at      DATETIME(3)    NULL,
    cancelled_at      DATETIME(3)    NULL,
    cancel_reason     VARCHAR(255)   NULL,
    created_at        DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_order_no (order_no),
    UNIQUE KEY uk_order_idem (tenant_id, idempotency_key),
    KEY idx_order_tenant_status (tenant_id, status, created_at),
    KEY idx_order_tenant_created (tenant_id, created_at),
    KEY idx_order_expiry (status, expires_at)
) ENGINE = InnoDB;

CREATE TABLE order_item (
    id            BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id     BIGINT         NOT NULL,
    order_id      BIGINT         NOT NULL,
    product_id    BIGINT         NOT NULL,
    sku           VARCHAR(64)    NOT NULL,
    product_name  VARCHAR(160)   NOT NULL,
    unit_price    DECIMAL(12, 2) NOT NULL,
    quantity      INT            NOT NULL,
    line_total    DECIMAL(14, 2) NOT NULL,
    KEY idx_item_order (tenant_id, order_id),
    KEY idx_item_product (tenant_id, product_id)
) ENGINE = InnoDB;

CREATE TABLE payment (
    id               BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id        BIGINT         NOT NULL,
    payment_no       VARCHAR(32)    NOT NULL,
    order_id         BIGINT         NOT NULL,
    amount           DECIMAL(14, 2) NOT NULL,
    method           VARCHAR(20)    NOT NULL,
    status           VARCHAR(20)    NOT NULL,
    provider_txn_id  VARCHAR(64)    NULL,
    idempotency_key  VARCHAR(64)    NULL,
    failure_reason   VARCHAR(255)   NULL,
    created_at       DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    paid_at          DATETIME(3)    NULL,
    UNIQUE KEY uk_payment_no (payment_no),
    UNIQUE KEY uk_payment_idem (tenant_id, idempotency_key),
    KEY idx_payment_order (tenant_id, order_id),
    KEY idx_payment_tenant (tenant_id, created_at)
) ENGINE = InnoDB;

-- Transactional outbox: written in the same transaction as the business change,
-- relayed to RabbitMQ asynchronously with publisher confirms.
CREATE TABLE outbox_event (
    id               BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_id         CHAR(36)     NOT NULL,
    tenant_id        BIGINT       NOT NULL,
    aggregate_type   VARCHAR(40)  NOT NULL,
    aggregate_id     VARCHAR(64)  NOT NULL,
    event_type       VARCHAR(60)  NOT NULL,
    payload          JSON         NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'NEW',
    attempts         INT          NOT NULL DEFAULT 0,
    next_attempt_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    last_error       VARCHAR(500) NULL,
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    sent_at          DATETIME(3)  NULL,
    UNIQUE KEY uk_outbox_event (event_id),
    KEY idx_outbox_pending (status, next_attempt_at)
) ENGINE = InnoDB;

-- Consumer-side de-duplication (at-least-once delivery => idempotent consumers).
CREATE TABLE consumed_message (
    message_id   CHAR(36)    NOT NULL,
    consumer     VARCHAR(60) NOT NULL,
    consumed_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (message_id, consumer)
) ENGINE = InnoDB;

CREATE TABLE notification (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id   BIGINT       NOT NULL,
    type        VARCHAR(40)  NOT NULL,
    level       VARCHAR(20)  NOT NULL DEFAULT 'INFO',
    title       VARCHAR(160) NOT NULL,
    content     VARCHAR(500) NULL,
    read_flag   TINYINT(1)   NOT NULL DEFAULT 0,
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_notification_tenant (tenant_id, read_flag, created_at)
) ENGINE = InnoDB;

CREATE TABLE audit_log (
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id    BIGINT       NOT NULL,
    user_id      BIGINT       NULL,
    user_email   VARCHAR(160) NULL,
    action       VARCHAR(60)  NOT NULL,
    target_type  VARCHAR(40)  NULL,
    target_id    VARCHAR(64)  NULL,
    detail       VARCHAR(500) NULL,
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_audit_tenant (tenant_id, created_at)
) ENGINE = InnoDB;
