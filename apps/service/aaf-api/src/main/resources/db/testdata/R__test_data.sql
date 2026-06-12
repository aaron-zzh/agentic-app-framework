-- ============================================================
-- 开发/测试环境种子数据（Flyway Repeatable Migration）
-- 每次内容变化自动重新执行
-- 生产环境通过 locations 配置排除此目录
-- ============================================================

-- ==================== 测试用户 ====================

-- 密码均为 admin（BCrypt）
INSERT INTO sys_user (username, password, nickname, email, email_verified, status)
VALUES ('user1', '$2a$10$UyqdQK.M7V9FE4IzbbzeUeQnU.NsumDR.RCviFq4Pt04Y/F4VWLKC', '用户1', 'user1@xuejiai.com', TRUE, 0),
       ('user2', '$2a$10$UyqdQK.M7V9FE4IzbbzeUeQnU.NsumDR.RCviFq4Pt04Y/F4VWLKC', '用户2', 'user2@xuejiai.com', TRUE, 0)
ON CONFLICT (username) DO NOTHING;

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u, sys_role r
WHERE u.username IN ('user1', 'user2') AND r.code = 'user'
ON CONFLICT (user_id, role_id) DO NOTHING;

INSERT INTO sys_organization (name, slug, type, owner_id, create_by)
SELECT u.nickname || '的空间', 'personal-' || u.id, 'personal', u.id, u.id
FROM sys_user u
WHERE u.username IN ('user1', 'user2')
  AND NOT EXISTS (SELECT 1 FROM sys_organization o WHERE o.slug = 'personal-' || u.id);

INSERT INTO sys_org_member (org_id, user_id, role, create_by)
SELECT o.id, o.owner_id, 'owner', o.owner_id
FROM sys_organization o
JOIN sys_user u ON o.owner_id = u.id
WHERE u.username IN ('user1', 'user2')
  AND NOT EXISTS (SELECT 1 FROM sys_org_member m WHERE m.org_id = o.id AND m.user_id = o.owner_id);


-- ==================== 积分测试数据 ====================

-- 为测试用户创建积分账户
INSERT INTO credit_account (user_id, balance, frozen, total_earned, total_spent)
SELECT u.id, 999, 0, 999, 0
FROM sys_user u WHERE u.username = 'admin'
  AND NOT EXISTS (SELECT 1 FROM credit_account ca WHERE ca.user_id = u.id);

INSERT INTO credit_account (user_id, balance, frozen, total_earned, total_spent)
SELECT u.id, 5000, 0, 5000, 0
FROM sys_user u WHERE u.username = 'user1'
  AND NOT EXISTS (SELECT 1 FROM credit_account ca WHERE ca.user_id = u.id);

INSERT INTO credit_account (user_id, balance, frozen, total_earned, total_spent)
SELECT u.id, 2000, 500, 3000, 500
FROM sys_user u WHERE u.username = 'user2'
  AND NOT EXISTS (SELECT 1 FROM credit_account ca WHERE ca.user_id = u.id);


-- 统一设置语言模型价格基准（输入 36元/1M Token，补全 108元/1M Token）
-- input_price_per_k 单位：元/千Token = 36/1000 = 0.036
-- output_price_per_k 单位：元/千Token = 108/1000 = 0.108
UPDATE ai_model
SET input_price_per_k  = 0.036000,
    output_price_per_k = 0.108000
WHERE model_type = 'TEXT' AND (input_price_per_k IS NULL OR input_price_per_k = 0);

-- 图像生成模型按次固定价格（quota_type=1，1元/次）
UPDATE ai_model
SET model_price = 1.000000,
    quota_type  = 1
WHERE model_type = 'IMAGE' AND (model_price IS NULL OR model_price = 0);
