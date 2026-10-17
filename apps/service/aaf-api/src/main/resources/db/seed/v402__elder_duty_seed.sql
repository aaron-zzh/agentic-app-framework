-- ============================================================
-- AAF-120 养老业务角色与服务账号种子
-- 本文件由 #12011、#12017 按分段追加；禁止重排其它任务分段。
-- ============================================================

-- ==================== #12011 养老运营与值班角色 ====================

INSERT INTO sys_role (code, name, description, status)
VALUES
    ('elder_operator', '养老运营', '养老业务日常运营角色', 0),
    ('elder_duty', '运营值班', '接收并处置养老安全预警的统一值班角色', 0)
ON CONFLICT (code) WHERE deleted = FALSE DO NOTHING;

-- ==================== #12011 默认值班服务账号 ====================

-- “!”是不可登录的密码标记，不是账号密码；部署管理员首次使用前必须重置密码。
INSERT INTO sys_user
    (username, password, nickname, status, email_verified, source_app, source_channel)
VALUES
    ('elder-duty', '!', '养老运营值班', 0, FALSE, 'service', 'seed')
ON CONFLICT (username) WHERE deleted = FALSE DO NOTHING;

INSERT INTO sys_user_role (user_id, role_id)
SELECT user_account.id, role.id
FROM sys_user user_account
JOIN sys_role role ON role.code = 'elder_duty' AND role.deleted = FALSE
WHERE user_account.username = 'elder-duty'
  AND user_account.deleted = FALSE
ON CONFLICT (user_id, role_id) DO NOTHING;

-- ==================== #12011 值班账号组织访问 ====================

-- 加入迁移时已存在的组织与工作区，后续新建组织需由管理员显式加入值班账号。
INSERT INTO sys_org_member (org_id, user_id, role, create_by)
SELECT organization.id, user_account.id, 'member', organization.owner_id
FROM sys_organization organization
JOIN sys_user user_account
    ON user_account.username = 'elder-duty' AND user_account.deleted = FALSE
WHERE organization.deleted = FALSE
  AND NOT EXISTS (
      SELECT 1
      FROM sys_org_member member
      WHERE member.org_id = organization.id
        AND member.user_id = user_account.id
        AND member.deleted = FALSE
  );

INSERT INTO sys_workspace_member
    (org_id, workspace_id, user_id, owner_id, create_by)
SELECT workspace.org_id,
       workspace.id,
       user_account.id,
       user_account.id,
       workspace.owner_id
FROM sys_workspace workspace
JOIN sys_user user_account
    ON user_account.username = 'elder-duty' AND user_account.deleted = FALSE
WHERE workspace.deleted = FALSE
  AND NOT EXISTS (
      SELECT 1
      FROM sys_workspace_member member
      WHERE member.workspace_id = workspace.id
        AND member.user_id = user_account.id
        AND member.deleted = FALSE
  );

-- ==================== #12017 助理角色种子 ====================

INSERT INTO ai_role
    (code, name, description, skill_ids, tool_whitelist, status, visibility,
     create_time, update_time, deleted)
VALUES
    (
        'system.role.elder-companion',
        '长者陈伴',
        '家庭陪伴机器人使用的长者陪伴角色，只能查询或经两步确认创建用药计划和预约',
        '[{"skillKey":"system.skill.elder-companion","activationMode":"ALWAYS"},{"skillKey":"system.skill.elder-care-health","activationMode":"ON_DEMAND"}]'::jsonb,
        '["listMedicationPlans","draftMedicationPlan","confirmMedicationPlan","listAvailableServices","draftServiceOrder","confirmServiceOrder","queryServiceOrderStatus","listRecentPhotos","searchRehabGuide","queryMedicalKnowledge","queryMedicalAnswer"]',
        'active', 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
    ),
    (
        'system.role.elder-family-service',
        '家属服务',
        '已授权家属查看长者概况、预警、用药、照片和服务预约的角色',
        '[{"skillKey":"system.skill.elder-family-service","activationMode":"ALWAYS"}]'::jsonb,
        '["queryElderOverview","queryAlertStatus","listMedicationPlans","listRecentPhotos","listAvailableServices","draftServiceOrder","confirmServiceOrder","queryServiceOrderStatus","searchRehabGuide"]',
        'active', 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
    ),
    (
        'system.role.elder-ops-assistant',
        '运营助手',
        '养老运营和值班人员的只读状态查询角色，不能起草或确认预约',
        '[{"skillKey":"system.skill.elder-ops-assistant","activationMode":"ALWAYS"}]'::jsonb,
        '["queryAlertStatus","queryDeviceStatus","queryServiceOrderStatus"]',
        'active', 'SYSTEM', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
    )
ON CONFLICT (code) WHERE deleted = FALSE DO UPDATE
SET name = EXCLUDED.name,
    description = EXCLUDED.description,
    skill_ids = EXCLUDED.skill_ids,
    tool_whitelist = EXCLUDED.tool_whitelist,
    status = EXCLUDED.status,
    visibility = EXCLUDED.visibility,
    update_time = CURRENT_TIMESTAMP;



-- ==================== #12016 长者陈伴角色挂载 ====================

-- 挂到系统默认模板供后续用户复制，同时补齐迁移时已经存在的默认用户副本。
INSERT INTO ai_assistant_role
    (assistant_id, role_id, is_default, sort_order, enabled, owner_id,
     create_time, update_time, deleted)
SELECT assistant.id,
       role.id,
       FALSE,
       60,
       TRUE,
       CASE WHEN assistant.user_id = 0 THEN NULL ELSE assistant.user_id END,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP,
       FALSE
FROM ai_assistant assistant
JOIN ai_role role
    ON role.code = 'system.role.elder-companion'
    AND role.deleted = FALSE
WHERE assistant.deleted = FALSE
  AND assistant.status = 'active'
  AND (
      assistant.code = 'system.assistant.default-user'
      OR (
          assistant.source_system_key = 'system.assistant.default-user'
          AND assistant.is_default = TRUE
      )
  )
ON CONFLICT (assistant_id, role_id) DO UPDATE
SET enabled = TRUE,
    sort_order = EXCLUDED.sort_order,
    update_time = CURRENT_TIMESTAMP,
    deleted = FALSE,
    delete_time = NULL;