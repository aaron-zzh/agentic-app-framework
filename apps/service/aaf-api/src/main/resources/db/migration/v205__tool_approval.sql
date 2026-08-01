-- M36：工具层人工确认持久化（收敛内存版 HITL）
-- 背景：framework 存在两套 HITL——
--   1) 任务级持久化主链 ai_hitl_approval（PersistentHitlCoordinator，需 AssistantTask/InvocationContext，含任务恢复）
--   2) 工具层内存版 HumanApprovalService（ConcurrentHashMap），由 ToolPermissionChecker / 内容审查发起
-- 内存版的致命问题：全代码库没有任何 resolve/getResult 调用方，创建的审批**永远无人可处理**，
-- 且重启/多实例即丢失；同时 publisher 已把卡片推给用户，用户点了也无处落地。
-- 工具层入口（ToolService REST 调用、Flowable ToolNode）本身不是 assistant 任务执行，
-- 没有 taskId/executionId，无法复用任务级表，故为其建立独立的持久化审批表。
--
-- 与 ai_hitl_approval 的分工：
--   ai_hitl_approval  —— 任务级审批，决定后驱动任务状态迁移与恢复
--   ai_tool_approval  —— 会话/工作流级工具确认，决定后回写会话级工具授权（grantScope）

CREATE TABLE IF NOT EXISTS ai_tool_approval (
    approval_id      VARCHAR(64)  PRIMARY KEY,
    scope_key        VARCHAR(128) NOT NULL,
    user_id          BIGINT       NOT NULL,
    approval_type    VARCHAR(32)  NOT NULL,
    title            VARCHAR(200) NOT NULL,
    description      VARCHAR(1000),
    subject_type     VARCHAR(32),
    subject_key      VARCHAR(200),
    risk_level       VARCHAR(16),
    confidence       DOUBLE PRECISION,
    grant_scope      VARCHAR(16)  NOT NULL DEFAULT 'NONE',
    context_json     TEXT,
    status           VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    decision_reason  VARCHAR(500),
    created_at       TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at       TIMESTAMP(6) NOT NULL,
    decided_at       TIMESTAMP(6),
    decided_by       BIGINT,
    version          INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT ck_tool_approval_status CHECK (status IN ('PENDING','APPROVED','REJECTED','TIMEOUT'))
);

COMMENT ON TABLE ai_tool_approval IS '工具层人工确认：会话/工作流级工具调用、内容审查、低置信度确认';
COMMENT ON COLUMN ai_tool_approval.scope_key IS '授权作用域键：会话 sessionId 或 workflow:<processInstanceId>；无会话时为 "-"';
COMMENT ON COLUMN ai_tool_approval.grant_scope IS '批准后授予范围：NONE/ONCE/SESSION/PATTERN，由监听器回写工具授权';
COMMENT ON COLUMN ai_tool_approval.status IS 'PENDING/APPROVED/REJECTED/TIMEOUT；TIMEOUT 由读取时按 expires_at 判定并落库';

CREATE INDEX IF NOT EXISTS idx_tool_approval_user_status ON ai_tool_approval (user_id, status);
CREATE INDEX IF NOT EXISTS idx_tool_approval_scope ON ai_tool_approval (scope_key, status);
