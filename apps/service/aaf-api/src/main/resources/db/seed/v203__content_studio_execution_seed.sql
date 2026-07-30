-- ============================================================
-- Content Studio 执行链路字典、绑定与权限种子
-- ============================================================

-- ---------------- 字典类型 ----------------
INSERT INTO sys_dict_type (name, type, status, remark) VALUES
('内容对象版本状态', 'content_object_version_status', 0, 'Content Studio 对象候选与采用状态'),
('内容动作', 'content_action_key', 0, 'Content Studio 内置动作展示标签')
ON CONFLICT DO NOTHING;

-- ---------------- 字典数据 ----------------
INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('content_object_version_status', '候选', 'candidate', 1, 'warning'),
('content_object_version_status', '已采用', 'adopted', 2, 'success'),
('content_object_version_status', '已否决', 'rejected', 3, 'danger'),
('content_object_version_status', '已被取代', 'superseded', 4, 'info')
ON CONFLICT DO NOTHING;

INSERT INTO sys_dict_data (dict_type, label, value, sort, color_type) VALUES
('content_action_key', '完善简报', 'brief.refine', 1, 'default'),
('content_action_key', '生成创意方向', 'concept.generate', 2, 'primary'),
('content_action_key', '生成文案', 'copy.generate', 3, 'info'),
('content_action_key', '生成图片', 'image.generate', 4, 'warning'),
('content_action_key', '局部修改图片', 'image.edit', 5, 'warning'),
('content_action_key', '重新生成交付物', 'deliverable.regenerate', 6, 'primary'),
('content_action_key', '品牌校验', 'brand.validate', 7, 'success'),
('content_action_key', '主张证据校验', 'claim.validate', 8, 'success')
ON CONFLICT DO NOTHING;

UPDATE sys_dict_data
SET label = '画布节点', value = 'canvas_board'
WHERE dict_type = 'content_object_type'
  AND value = 'inspiration_board';

-- ---------------- 内置执行绑定 ----------------
-- 当前只种真实接通的 Tool 分支；Agent/Workflow 待有稳定运行时契约后再配置。
INSERT INTO cs_execution_binding
    (action_key, target_type, target_ref, binding_version, priority,
     confirmation_required, estimated_credits, status)
VALUES
('brief.refine', 'tool', 'copywriting.generate', '1.0.0', 0, TRUE, 1.00, 'published'),
('concept.generate', 'tool', 'copywriting.generate', '1.0.0', 0, TRUE, 1.00, 'published'),
('copy.generate', 'tool', 'copywriting.generate', '1.0.0', 0, TRUE, 1.00, 'published'),
('image.generate', 'tool', 'aigc.image.generate', '1.0.0', 0, TRUE, 10.00, 'published'),
('image.edit', 'tool', 'aigc.image.edit', '1.0.0', 0, TRUE, 10.00, 'published'),
('deliverable.regenerate', 'tool', 'copywriting.generate', '1.0.0', 0, TRUE, 1.00, 'published'),
('brand.validate', 'tool', 'copywriting.generate', '1.0.0', 0, TRUE, 1.00, 'published'),
('claim.validate', 'tool', 'copywriting.generate', '1.0.0', 0, TRUE, 1.00, 'published')
ON CONFLICT DO NOTHING;

-- ---------------- 蓝图动作键修正 ----------------
UPDATE cs_project_blueprint
SET action_keys = '["brief.refine","concept.generate","copy.generate","image.generate","image.edit","deliverable.regenerate","brand.validate","claim.validate"]'::jsonb
WHERE status = 'published';

-- ---------------- 资源权限码 ----------------
INSERT INTO sys_permission_code (name, code, module, resource, action, status)
VALUES ('执行内容项目动作', 'content:project:action', 'content', 'project', 'action', 0)
ON CONFLICT (code) DO NOTHING;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
JOIN sys_permission_code permission ON permission.code = 'content:project:action'
WHERE role.code IN ('member', 'org_admin', 'admin', 'super_admin')
ON CONFLICT DO NOTHING;
