-- 修复 3D 系统默认模型：Meshy 未启用，改用已入库的阿里云百炼 Tripo 3D。
UPDATE ai_model_preference
SET model_ids = '["tripo:tripo3d-v2"]'::jsonb,
    version = version + 1,
    update_time = CURRENT_TIMESTAMP
WHERE scope = 'SYSTEM'
  AND scope_id IS NULL
  AND capability = 'MODEL_3D'
  AND deleted = FALSE
  AND model_ids = '["meshy:meshy-4"]'::jsonb;

INSERT INTO ai_model_preference (scope, scope_id, capability, model_ids)
SELECT 'SYSTEM', NULL, 'MODEL_3D', '["tripo:tripo3d-v2"]'::jsonb
WHERE NOT EXISTS (
    SELECT 1
    FROM ai_model_preference
    WHERE scope = 'SYSTEM'
      AND scope_id IS NULL
      AND capability = 'MODEL_3D'
      AND deleted = FALSE
);
