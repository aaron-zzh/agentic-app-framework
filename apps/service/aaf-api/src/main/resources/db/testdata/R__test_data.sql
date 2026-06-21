-- ============================================================
-- 开发/测试/演示环境种子数据（Flyway Repeatable Migration）
-- 每次内容变化自动重新执行
-- 生产环境通过 locations 配置排除此目录
-- 演示环境通过 /api/system/demo/load 和 /clean 按需加载/清理
-- ============================================================

-- 清空标记表，确保重复执行幂等
DELETE FROM sys_demo_data_record;

-- ==================== 测试用户 ====================

-- 密码均为 admin（BCrypt）
INSERT INTO sys_user (username, password, nickname, email, email_verified, status)
VALUES ('user1', '$2a$10$UyqdQK.M7V9FE4IzbbzeUeQnU.NsumDR.RCviFq4Pt04Y/F4VWLKC', '用户1', 'user1@xuejiai.com', TRUE, 0),
       ('user2', '$2a$10$UyqdQK.M7V9FE4IzbbzeUeQnU.NsumDR.RCviFq4Pt04Y/F4VWLKC', '用户2', 'user2@xuejiai.com', TRUE, 0)
ON CONFLICT (username) DO NOTHING;

INSERT INTO sys_demo_data_record (table_name, record_id)
SELECT 'sys_user', id FROM sys_user WHERE username IN ('user1', 'user2');

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u, sys_role r
WHERE u.username IN ('user1', 'user2') AND r.code = 'user'
ON CONFLICT (user_id, role_id) DO NOTHING;

INSERT INTO sys_organization (name, slug, type, owner_id, create_by)
SELECT u.nickname || '的空间', 'personal-' || u.id, 'personal', u.id, u.id
FROM sys_user u
WHERE u.username IN ('user1', 'user2')
  AND NOT EXISTS (SELECT 1 FROM sys_organization o WHERE o.slug = 'personal-' || u.id);

INSERT INTO sys_demo_data_record (table_name, record_id)
SELECT 'sys_organization', id FROM sys_organization
WHERE owner_id IN (SELECT id FROM sys_user WHERE username IN ('user1', 'user2'));

INSERT INTO sys_org_member (org_id, user_id, role, create_by)
SELECT o.id, o.owner_id, 'owner', o.owner_id
FROM sys_organization o
JOIN sys_user u ON o.owner_id = u.id
WHERE u.username IN ('user1', 'user2')
  AND NOT EXISTS (SELECT 1 FROM sys_org_member m WHERE m.org_id = o.id AND m.user_id = o.owner_id);


-- ==================== 积分测试数据 ====================

WITH inserted AS (
    INSERT INTO credit_account (user_id, balance, frozen, total_earned, total_spent)
    SELECT u.id, 999, 0, 999, 0
    FROM sys_user u WHERE u.username = 'admin'
      AND NOT EXISTS (SELECT 1 FROM credit_account ca WHERE ca.user_id = u.id)
    RETURNING id
)
INSERT INTO sys_demo_data_record (table_name, record_id)
SELECT 'credit_account', id FROM inserted;

WITH inserted AS (
    INSERT INTO credit_account (user_id, balance, frozen, total_earned, total_spent)
    SELECT u.id, 50, 0, 50, 0
    FROM sys_user u WHERE u.username = 'user1'
      AND NOT EXISTS (SELECT 1 FROM credit_account ca WHERE ca.user_id = u.id)
    RETURNING id
)
INSERT INTO sys_demo_data_record (table_name, record_id)
SELECT 'credit_account', id FROM inserted;

WITH inserted AS (
    INSERT INTO credit_account (user_id, balance, frozen, total_earned, total_spent)
    SELECT u.id, 30, 0, 30, 0
    FROM sys_user u WHERE u.username = 'user2'
      AND NOT EXISTS (SELECT 1 FROM credit_account ca WHERE ca.user_id = u.id)
    RETURNING id
)
INSERT INTO sys_demo_data_record (table_name, record_id)
SELECT 'credit_account', id FROM inserted;

-- 为测试账户补充 EARN 流水，使 /api/credits/groups 能正确汇总分组余额
WITH inserted AS (
    INSERT INTO credit_transaction (account_id, type, amount, balance_after, source, batch_type, remain, deleted)
    SELECT ca.id, 'EARN', 999, 999, 'manual', 'MANUAL', 999, false
    FROM credit_account ca JOIN sys_user u ON ca.user_id = u.id
    WHERE u.username = 'admin'
      AND NOT EXISTS (SELECT 1 FROM credit_transaction ct WHERE ct.account_id = ca.id AND ct.type = 'EARN')
    RETURNING id
)
INSERT INTO sys_demo_data_record (table_name, record_id)
SELECT 'credit_transaction', id FROM inserted;

WITH inserted AS (
    INSERT INTO credit_transaction (account_id, type, amount, balance_after, source, batch_type, remain, deleted)
    SELECT ca.id, 'EARN', 50, 50, 'manual', 'SUBSCRIPTION', 50, false
    FROM credit_account ca JOIN sys_user u ON ca.user_id = u.id
    WHERE u.username = 'user1'
      AND NOT EXISTS (SELECT 1 FROM credit_transaction ct WHERE ct.account_id = ca.id AND ct.type = 'EARN')
    RETURNING id
)
INSERT INTO sys_demo_data_record (table_name, record_id)
SELECT 'credit_transaction', id FROM inserted;

WITH inserted AS (
    INSERT INTO credit_transaction (account_id, type, amount, balance_after, source, batch_type, remain, deleted)
    SELECT ca.id, 'EARN', 30, 30, 'manual', 'SUBSCRIPTION', 30, false
    FROM credit_account ca JOIN sys_user u ON ca.user_id = u.id
    WHERE u.username = 'user2'
      AND NOT EXISTS (SELECT 1 FROM credit_transaction ct WHERE ct.account_id = ca.id AND ct.type = 'EARN')
    RETURNING id
)
INSERT INTO sys_demo_data_record (table_name, record_id)
SELECT 'credit_transaction', id FROM inserted;

-- ==================== 通知演示数据 ====================

-- 插入系统公告（演示用），并将通知 relatedUrl 指向公告详情页
WITH notice1 AS (
    INSERT INTO sys_notice (title, content, type, status, publish_time, create_by)
    SELECT '2026年Q2系统升级公告', '系统将于2026年5月17日22:00-22:30进行例行维护升级，升级期间服务暂停，请提前做好安排。', 'ANNOUNCEMENT', 1, '2026-05-17 09:00:00',
           (SELECT id FROM sys_user WHERE username = 'admin')
    WHERE NOT EXISTS (SELECT 1 FROM sys_notice WHERE title = '2026年Q2系统升级公告')
    RETURNING id
),
notice2 AS (
    INSERT INTO sys_notice (title, content, type, status, publish_time, create_by)
    SELECT '关于报销流程优化的通知', '自2026年5月起，报销申请统一通过系统提交，纸质单据不再受理，请相关人员知悉。', 'NOTICE', 1, '2026-05-16 16:00:00',
           (SELECT id FROM sys_user WHERE username = 'admin')
    WHERE NOT EXISTS (SELECT 1 FROM sys_notice WHERE title = '关于报销流程优化的通知')
    RETURNING id
)
SELECT 1;

-- 为 admin 插入演示通知，relatedUrl 指向公告详情
INSERT INTO sys_notification (user_id, type, title, body, is_read, related_url, create_time)
SELECT u.id, 'approval', '张三提交了报销单', '金额 ¥3,200，等待您审批', false,
       '/notices/' || (SELECT id FROM sys_notice WHERE title = '关于报销流程优化的通知'),
       '2026-05-17 15:20:00'
FROM sys_user u WHERE u.username = 'admin'
  AND NOT EXISTS (SELECT 1 FROM sys_notification n WHERE n.user_id = u.id AND n.title = '张三提交了报销单');

INSERT INTO sys_notification (user_id, type, title, body, is_read, related_url, create_time)
SELECT u.id, 'mention', '李四在文档中 @了您', 'Q2 季度报告需要您确认数据', false, null, '2026-05-17 14:30:00'
FROM sys_user u WHERE u.username = 'admin'
  AND NOT EXISTS (SELECT 1 FROM sys_notification n WHERE n.user_id = u.id AND n.title = '李四在文档中 @了您');

INSERT INTO sys_notification (user_id, type, title, body, is_read, related_url, create_time)
SELECT u.id, 'task', '您有一条待处理任务已逾期', '客户跟进 - 腾讯科技', false, null, '2026-05-17 10:00:00'
FROM sys_user u WHERE u.username = 'admin'
  AND NOT EXISTS (SELECT 1 FROM sys_notification n WHERE n.user_id = u.id AND n.title = '您有一条待处理任务已逾期');

INSERT INTO sys_notification (user_id, type, title, body, is_read, related_url, create_time)
SELECT u.id, 'system', '系统将于今晚 22:00 维护', '预计维护时间 30 分钟', false,
       '/notices/' || (SELECT id FROM sys_notice WHERE title = '2026年Q2系统升级公告'),
       '2026-05-17 09:00:00'
FROM sys_user u WHERE u.username = 'admin'
  AND NOT EXISTS (SELECT 1 FROM sys_notification n WHERE n.user_id = u.id AND n.title = '系统将于今晚 22:00 维护');

INSERT INTO sys_notification (user_id, type, title, body, is_read, related_url, create_time)
SELECT u.id, 'approval', '王五的请假申请已通过', NULL, true, null, '2026-05-16 16:00:00'
FROM sys_user u WHERE u.username = 'admin'
  AND NOT EXISTS (SELECT 1 FROM sys_notification n WHERE n.user_id = u.id AND n.title = '王五的请假申请已通过');

INSERT INTO sys_demo_data_record (table_name, record_id)
SELECT 'sys_notice', id FROM sys_notice
WHERE title IN ('2026年Q2系统升级公告', '关于报销流程优化的通知');

INSERT INTO sys_demo_data_record (table_name, record_id)
SELECT 'sys_notification', n.id FROM sys_notification n
JOIN sys_user u ON n.user_id = u.id
WHERE u.username = 'admin'
  AND n.title IN (
    '张三提交了报销单',
    '李四在文档中 @了您',
    '您有一条待处理任务已逾期',
    '系统将于今晚 22:00 维护',
    '王五的请假申请已通过'
  );
