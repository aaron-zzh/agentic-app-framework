-- ============================================================
-- 演示数据清理脚本
-- 按 sys_demo_data_record 标记表精确删除，无需硬编码业务字段
-- 外键依赖逆序：子表先删，父表后删
-- ============================================================

DELETE FROM credit_transaction
WHERE id IN (SELECT record_id FROM sys_demo_data_record WHERE table_name = 'credit_transaction');

DELETE FROM credit_account
WHERE id IN (SELECT record_id FROM sys_demo_data_record WHERE table_name = 'credit_account');

DELETE FROM sys_notification
WHERE id IN (SELECT record_id FROM sys_demo_data_record WHERE table_name = 'sys_notification');

DELETE FROM sys_notice
WHERE id IN (SELECT record_id FROM sys_demo_data_record WHERE table_name = 'sys_notice');

DELETE FROM sys_org_member
WHERE user_id IN (SELECT record_id FROM sys_demo_data_record WHERE table_name = 'sys_user');

DELETE FROM sys_user_role
WHERE user_id IN (SELECT record_id FROM sys_demo_data_record WHERE table_name = 'sys_user');

DELETE FROM sys_organization
WHERE id IN (SELECT record_id FROM sys_demo_data_record WHERE table_name = 'sys_organization');

DELETE FROM sys_user
WHERE id IN (SELECT record_id FROM sys_demo_data_record WHERE table_name = 'sys_user');

DELETE FROM sys_demo_data_record;
