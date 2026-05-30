-- ============================================================
-- 资源类权益定义 + FREE 套餐 + 套餐权益挂接
-- ============================================================

-- ==================== 资源类权益定义 ====================

INSERT INTO entitlement_def (code, name, type, unit, description)
VALUES
('kb_storage',      '知识库存储',   'COUNTABLE', 'GB',  '知识库可用存储空间上限'),
('image_storage',   '图像存储',     'COUNTABLE', '张',  '图像素材库存储数量上限'),
('agent_count',     'Agent 数量',   'COUNTABLE', '个',  '可创建的 Agent 数量上限'),
('workflow_count',  '工作流数量',   'COUNTABLE', '个',  '可创建的工作流数量上限')
ON CONFLICT DO NOTHING;

-- ==================== FREE 套餐 ====================

INSERT INTO subscription_plan (code, name, duration_days, price, market_price, status, sort)
VALUES ('FREE', '免费套餐', 0, 0, 0, 'ENABLED', 0)
ON CONFLICT DO NOTHING;

-- ==================== FREE 套餐权益挂接 ====================

INSERT INTO plan_entitlement (plan_id, ent_id, quota, reset_cycle, refill_price)
SELECT p.id, e.id, v.quota, v.reset_cycle, 0
FROM subscription_plan p
CROSS JOIN (VALUES
    ('kb_storage',     1,    'NONE'),
    ('image_storage',  100,  'NONE'),
    ('agent_count',    3,    'NONE'),
    ('workflow_count', 5,    'NONE')
) AS v(code, quota, reset_cycle)
JOIN entitlement_def e ON e.code = v.code AND e.deleted = FALSE
WHERE p.code = 'FREE' AND p.deleted = FALSE
ON CONFLICT DO NOTHING;
