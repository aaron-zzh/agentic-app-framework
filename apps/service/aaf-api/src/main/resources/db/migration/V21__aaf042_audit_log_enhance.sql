-- 审计日志增强：添加链式哈希校验字段
ALTER TABLE sys_audit_log ADD COLUMN hash          VARCHAR(64);
ALTER TABLE sys_audit_log ADD COLUMN previous_hash VARCHAR(64);
