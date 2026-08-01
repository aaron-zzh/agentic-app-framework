-- M19：行为事件补组织维度
-- 背景：sys_user_event 无 org_id，漏斗/留存/画像等聚合查询无法按组织过滤，
-- 组织管理员一旦获得分析权限即可读取跨组织运营数据（含用户行为 PII 聚合）。
-- 方案：追加 org_id（采集时从 OrgContext 写入），聚合查询对非平台管理员强制附加 org 条件。

ALTER TABLE sys_user_event ADD COLUMN IF NOT EXISTS org_id BIGINT;

COMMENT ON COLUMN sys_user_event.org_id IS '所属组织 ID，NULL=平台级/无组织上下文采集';

CREATE INDEX IF NOT EXISTS idx_user_event_org_time ON sys_user_event (org_id, create_time);
