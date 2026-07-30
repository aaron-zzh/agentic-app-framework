-- ============================================================
-- Content Studio 假校验绑定清理与蓝图动作收窄
-- ============================================================

-- 品牌与主张校验尚无真实校验执行器，禁止继续伪装为文案生成能力。
DELETE FROM cs_execution_binding
WHERE action_key IN ('brand.validate', 'claim.validate');

-- 图文类蓝图仅声明当前已真实接通的图文生产动作。
UPDATE cs_project_blueprint
SET action_keys = '["brief.refine","concept.generate","copy.generate","image.generate","deliverable.regenerate"]'::jsonb
WHERE code IN (
    'new-product-standard',
    'promotion-standard',
    'store-standard',
    'social-standard',
    'personal-ip-standard')
  AND status = 'published';

-- 品牌视觉蓝图保留图片编辑，不声明文案交付动作。
UPDATE cs_project_blueprint
SET action_keys = '["brief.refine","concept.generate","image.generate","image.edit","deliverable.regenerate"]'::jsonb
WHERE code = 'brand-visual-standard'
  AND status = 'published';

-- 叙事蓝图只声明当前已有真实绑定的策划与图文动作。
UPDATE cs_project_blueprint
SET action_keys = '["brief.refine","concept.generate","copy.generate","image.generate"]'::jsonb
WHERE code = 'narrative-series-short-drama'
  AND status = 'published';
