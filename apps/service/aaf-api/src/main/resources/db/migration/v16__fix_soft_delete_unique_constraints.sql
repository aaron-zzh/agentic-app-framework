-- 修复软删除表的唯一约束语义：全表级 UNIQUE 改为部分唯一索引（WHERE deleted = FALSE）。
--
-- 背景：以下表对应的 JPA 实体使用 @SQLDelete 做软删除（UPDATE ... SET deleted = true），
-- 但迁移脚本中唯一字段用的是全表级 UNIQUE 约束，导致软删除后该值无法被新记录复用，
-- 插入时报 duplicate key value violates unique constraint。
-- 与项目既有正确模式统一（如 uk_sys_entity_def_slug、idx_skill_code_unique）。
--
-- sys_user.username 纳入本次修复：注销/软删除后用户名允许被复用（关联关系均基于不可变 user_id，
-- 非 username），与消费级产品通行做法一致，且项目当前无用户名审计留存/黑名单机制。

ALTER TABLE sys_user DROP CONSTRAINT sys_user_username_key;
CREATE UNIQUE INDEX uk_sys_user_username ON sys_user (username) WHERE deleted = FALSE;

-- uk_sys_user_email 原条件只排除 email IS NULL，未排除软删除记录，同样导致复用冲突。
DROP INDEX uk_sys_user_email;
CREATE UNIQUE INDEX uk_sys_user_email ON sys_user (email) WHERE email IS NOT NULL AND deleted = FALSE;

ALTER TABLE sys_role DROP CONSTRAINT sys_role_code_key;
CREATE UNIQUE INDEX uk_sys_role_code ON sys_role (code) WHERE deleted = FALSE;

ALTER TABLE sys_organization DROP CONSTRAINT sys_organization_slug_key;
CREATE UNIQUE INDEX uk_sys_organization_slug ON sys_organization (slug) WHERE deleted = FALSE;

ALTER TABLE sys_config DROP CONSTRAINT sys_config_config_key_key;
CREATE UNIQUE INDEX uk_sys_config_config_key ON sys_config (config_key) WHERE deleted = FALSE;

ALTER TABLE sys_sequence DROP CONSTRAINT sys_sequence_code_key;
CREATE UNIQUE INDEX uk_sys_sequence_code ON sys_sequence (code) WHERE deleted = FALSE;

ALTER TABLE sys_message_template DROP CONSTRAINT sys_message_template_code_key;
CREATE UNIQUE INDEX uk_sys_message_template_code ON sys_message_template (code) WHERE deleted = FALSE;

ALTER TABLE sys_sms_template DROP CONSTRAINT sys_sms_template_code_key;
CREATE UNIQUE INDEX uk_sys_sms_template_code ON sys_sms_template (code) WHERE deleted = FALSE;

ALTER TABLE sys_mail_template DROP CONSTRAINT sys_mail_template_code_key;
CREATE UNIQUE INDEX uk_sys_mail_template_code ON sys_mail_template (code) WHERE deleted = FALSE;

ALTER TABLE sys_file DROP CONSTRAINT sys_file_file_key_key;
CREATE UNIQUE INDEX uk_sys_file_file_key ON sys_file (file_key) WHERE deleted = FALSE;

ALTER TABLE sys_permission_code DROP CONSTRAINT sys_permission_code_code_key;
CREATE UNIQUE INDEX uk_sys_permission_code_code ON sys_permission_code (code) WHERE deleted = FALSE;

ALTER TABLE sys_notification_preference DROP CONSTRAINT sys_notification_preference_user_id_key;
CREATE UNIQUE INDEX uk_sys_notification_preference_user_id ON sys_notification_preference (user_id) WHERE deleted = FALSE;

ALTER TABLE ai_model_provider DROP CONSTRAINT ai_model_provider_provider_code_key;
CREATE UNIQUE INDEX uk_ai_model_provider_provider_code ON ai_model_provider (provider_code) WHERE deleted = FALSE;

ALTER TABLE ai_tool_catalog DROP CONSTRAINT ai_tool_catalog_tool_name_key;
CREATE UNIQUE INDEX uk_ai_tool_catalog_tool_name ON ai_tool_catalog (tool_name) WHERE deleted = FALSE;

ALTER TABLE autodev_doc DROP CONSTRAINT autodev_doc_file_path_key;
CREATE UNIQUE INDEX uk_autodev_doc_file_path ON autodev_doc (file_path) WHERE deleted = FALSE;

ALTER TABLE company_ops_metric DROP CONSTRAINT company_ops_metric_code_key;
CREATE UNIQUE INDEX uk_company_ops_metric_code ON company_ops_metric (code) WHERE deleted = FALSE;

ALTER TABLE livechat_ticket DROP CONSTRAINT livechat_ticket_ticket_no_key;
CREATE UNIQUE INDEX uk_livechat_ticket_ticket_no ON livechat_ticket (ticket_no) WHERE deleted = FALSE;

-- uk_brokerage_invite_code_code 原索引无任何条件，同样导致软删除后 code 无法复用。
DROP INDEX uk_brokerage_invite_code_code;
CREATE UNIQUE INDEX uk_brokerage_invite_code_code ON brokerage_invite_code (code) WHERE deleted = FALSE;

-- 以下 5 处为表级/内联命名 UNIQUE 约束，同样缺少软删除排除条件。
ALTER TABLE avatar_outfit DROP CONSTRAINT uq_outfit_code;
CREATE UNIQUE INDEX uk_avatar_outfit_code ON avatar_outfit (code) WHERE deleted = FALSE;

ALTER TABLE credit_redeem_code DROP CONSTRAINT credit_redeem_code_code_hash_key;
CREATE UNIQUE INDEX uk_credit_redeem_code_code_hash ON credit_redeem_code (code_hash) WHERE deleted = FALSE;

ALTER TABLE user_project_template DROP CONSTRAINT uq_upt_code;
CREATE UNIQUE INDEX uk_user_project_template_code ON user_project_template (code) WHERE deleted = FALSE;

ALTER TABLE user_workflow_template DROP CONSTRAINT uq_uwt_code;
CREATE UNIQUE INDEX uk_user_workflow_template_code ON user_workflow_template (code) WHERE deleted = FALSE;

ALTER TABLE user_growth_task DROP CONSTRAINT uq_ugt_code;
CREATE UNIQUE INDEX uk_user_growth_task_code ON user_growth_task (code) WHERE deleted = FALSE;

-- ============================================================
-- 待办管理菜单路径迁移：/todos → /module/todo
-- ============================================================

UPDATE sys_menu
SET path = '/module/todo'
WHERE path = '/todos'
  AND deleted = false;
