-- AAF-031 #3106 乐观锁：为所有实体表添加 version 列（JPA @Version 乐观锁）
-- doc_document 已有 version 列，跳过

-- ==================== V1 已有表 ====================

ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE autodev_request ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE autodev_generated_code ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE autodev_execution_log ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

-- ==================== V2 新表 ====================

ALTER TABLE sys_notification ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE sys_notification_preference ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE sys_record_version ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE sys_activity_log ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE sys_comment ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE sys_subscription ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE sys_todo ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;
