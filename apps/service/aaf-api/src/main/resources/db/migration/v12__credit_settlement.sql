-- 积分与结算模块表结构
-- credit_account: 积分账户
-- credit_transaction: 积分流水
-- pay_order: 支付订单
-- biz_order: 业务订单
-- credit_token_rule: 积分转Token规则

-- ==================== 积分账户 ====================
CREATE TABLE credit_account (
    id              BIGSERIAL PRIMARY KEY,
    version         INTEGER NOT NULL DEFAULT 0,
    org_id          BIGINT,
    workspace_id    BIGINT,
    create_by       BIGINT,
    create_by_type  VARCHAR(16),
    create_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT,
    update_by_type  VARCHAR(16),
    update_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    owner_id        BIGINT,
    delete_time     TIMESTAMP,
    deleted         BOOLEAN NOT NULL DEFAULT FALSE,
    remark          TEXT,

    user_id         BIGINT NOT NULL,
    balance         BIGINT NOT NULL DEFAULT 0,
    frozen          BIGINT NOT NULL DEFAULT 0,
    total_earned    BIGINT NOT NULL DEFAULT 0,
    total_spent     BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_credit_account_user ON credit_account(user_id) WHERE deleted = FALSE;
COMMENT ON TABLE credit_account IS '积分账户';

-- ==================== 积分流水 ====================
CREATE TABLE credit_transaction (
    id              BIGSERIAL PRIMARY KEY,
    version         INTEGER NOT NULL DEFAULT 0,
    org_id          BIGINT,
    workspace_id    BIGINT,
    create_by       BIGINT,
    create_by_type  VARCHAR(16),
    create_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT,
    update_by_type  VARCHAR(16),
    update_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    owner_id        BIGINT,
    delete_time     TIMESTAMP,
    deleted         BOOLEAN NOT NULL DEFAULT FALSE,
    remark          TEXT,

    account_id      BIGINT NOT NULL,
    type            VARCHAR(20) NOT NULL,
    amount          BIGINT NOT NULL,
    balance_after   BIGINT NOT NULL,
    source          VARCHAR(100),
    biz_id          VARCHAR(64)
);

CREATE INDEX idx_credit_transaction_account ON credit_transaction(account_id) WHERE deleted = FALSE;
CREATE INDEX idx_credit_transaction_biz ON credit_transaction(biz_id) WHERE deleted = FALSE;
COMMENT ON TABLE credit_transaction IS '积分流水记录';

-- ==================== 支付订单 ====================
CREATE TABLE pay_order (
    id                  BIGSERIAL PRIMARY KEY,
    version             INTEGER NOT NULL DEFAULT 0,
    org_id              BIGINT,
    workspace_id        BIGINT,
    create_by           BIGINT,
    create_by_type      VARCHAR(16),
    create_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by           BIGINT,
    update_by_type      VARCHAR(16),
    update_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    owner_id            BIGINT,
    delete_time         TIMESTAMP,
    deleted             BOOLEAN NOT NULL DEFAULT FALSE,
    remark              TEXT,

    merchant_order_no   VARCHAR(64) NOT NULL,
    subject             VARCHAR(200) NOT NULL,
    body                VARCHAR(500),
    amount              BIGINT NOT NULL,
    status              INTEGER NOT NULL DEFAULT 0,
    channel_code        VARCHAR(32) NOT NULL,
    channel_order_no    VARCHAR(128),
    user_id             BIGINT NOT NULL,
    expire_time         TIMESTAMP,
    success_time        TIMESTAMP,
    refund_amount       BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_pay_order_merchant_no ON pay_order(merchant_order_no) WHERE deleted = FALSE;
CREATE INDEX idx_pay_order_user ON pay_order(user_id) WHERE deleted = FALSE;
COMMENT ON TABLE pay_order IS '支付订单';

-- ==================== 业务订单 ====================
CREATE TABLE biz_order (
    id              BIGSERIAL PRIMARY KEY,
    version         INTEGER NOT NULL DEFAULT 0,
    org_id          BIGINT,
    workspace_id    BIGINT,
    create_by       BIGINT,
    create_by_type  VARCHAR(16),
    create_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT,
    update_by_type  VARCHAR(16),
    update_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    owner_id        BIGINT,
    delete_time     TIMESTAMP,
    deleted         BOOLEAN NOT NULL DEFAULT FALSE,
    remark          TEXT,

    order_no        VARCHAR(64) NOT NULL,
    user_id         BIGINT NOT NULL,
    order_type      VARCHAR(32) NOT NULL,
    subject         VARCHAR(200) NOT NULL,
    total_amount    BIGINT NOT NULL,
    pay_order_id    BIGINT,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING'
);

CREATE UNIQUE INDEX uk_biz_order_no ON biz_order(order_no) WHERE deleted = FALSE;
CREATE INDEX idx_biz_order_user ON biz_order(user_id) WHERE deleted = FALSE;
CREATE INDEX idx_biz_order_pay ON biz_order(pay_order_id) WHERE deleted = FALSE;
COMMENT ON TABLE biz_order IS '业务订单';

-- ==================== 积分转Token规则 ====================
CREATE TABLE credit_token_rule (
    id              BIGSERIAL PRIMARY KEY,
    version         INTEGER NOT NULL DEFAULT 0,
    org_id          BIGINT,
    workspace_id    BIGINT,
    create_by       BIGINT,
    create_by_type  VARCHAR(16),
    create_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by       BIGINT,
    update_by_type  VARCHAR(16),
    update_time     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    owner_id        BIGINT,
    delete_time     TIMESTAMP,
    deleted         BOOLEAN NOT NULL DEFAULT FALSE,
    remark          TEXT,

    name            VARCHAR(100) NOT NULL,
    credit_amount   BIGINT NOT NULL,
    token_amount    BIGINT NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
    priority        INTEGER NOT NULL DEFAULT 0,
    effective_from  TIMESTAMP,
    effective_to    TIMESTAMP
);

COMMENT ON TABLE credit_token_rule IS '积分转Token规则';
