-- Phase 4: 数字人视频生成执行器配置
-- 用途: 添加视频生成相关的执行器绑定配置
-- 依赖: v24__digital_human_blueprint_seed.sql

-- ============================================================
-- 1. 数字人视频生成统一执行器
-- ============================================================
-- 根据视频时长自动路由：
--   - ≤15秒: 阿里云简单视频生成
--   - >15秒: Hyperframes多场景合成
INSERT INTO aigc_execution_binding (code, name, version, target_type, description, status, config, created_at, updated_at)
VALUES (
    'digital_human_video_generation',
    '数字人视频生成',
    '1.0.0',
    'TOOL',
    '数字人视频生成统一执行器，自动根据时长路由到阿里云或Hyperframes',
    'published',
    '{
        "routingStrategy": "duration_based",
        "providers": {
            "aliyun": {
                "maxDuration": 15,
                "apiEndpoint": "https://api.aliyun.com/digitalhuman/video/generate",
                "timeout": 180000,
                "supportedFormats": ["mp4"],
                "supportedRatios": ["16:9", "9:16", "1:1"]
            },
            "hyperframes": {
                "apiEndpoint": "https://api.hyperframes.ai/v1/compose",
                "timeout": 600000,
                "maxScenes": 50,
                "maxTotalDuration": 600,
                "supportedFormats": ["mp4"],
                "supportedRatios": ["16:9", "9:16", "1:1"]
            }
        },
        "defaultRatio": "16:9",
        "sceneSegmentation": {
            "enabled": true,
            "maxSceneDuration": 15,
            "splitBySentence": true,
            "punctuation": ["。", "！", "？", "；"]
        }
    }'::jsonb,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    version = EXCLUDED.version,
    description = EXCLUDED.description,
    config = EXCLUDED.config,
    updated_at = CURRENT_TIMESTAMP;

-- ============================================================
-- 2. 阿里云简单视频生成执行器（可选，供直接调用）
-- ============================================================
INSERT INTO aigc_execution_binding (code, name, version, target_type, description, status, config, created_at, updated_at)
VALUES (
    'aliyun_video_generation',
    '阿里云视频生成',
    '1.0.0',
    'TOOL',
    '阿里云数字人视频生成API，适用于≤15秒的单场景视频',
    'published',
    '{
        "provider": "aliyun",
        "apiEndpoint": "https://api.aliyun.com/digitalhuman/video/generate",
        "timeout": 180000,
        "maxDuration": 15,
        "supportedFormats": ["mp4"],
        "supportedRatios": ["16:9", "9:16", "1:1"],
        "rateLimit": {
            "requestsPerMinute": 10,
            "requestsPerDay": 1000
        }
    }'::jsonb,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    version = EXCLUDED.version,
    description = EXCLUDED.description,
    config = EXCLUDED.config,
    updated_at = CURRENT_TIMESTAMP;

-- ============================================================
-- 3. Hyperframes多场景视频合成执行器（可选，供直接调用）
-- ============================================================
INSERT INTO aigc_execution_binding (code, name, version, target_type, description, status, config, created_at, updated_at)
VALUES (
    'hyperframes_integration',
    'Hyperframes视频合成',
    '1.0.0',
    'TOOL',
    'Hyperframes多场景视频合成API，适用于>15秒的复杂视频',
    'published',
    '{
        "provider": "hyperframes",
        "apiEndpoint": "https://api.hyperframes.ai/v1/compose",
        "timeout": 600000,
        "maxScenes": 50,
        "maxTotalDuration": 600,
        "supportedFormats": ["mp4"],
        "supportedRatios": ["16:9", "9:16", "1:1"],
        "sceneTransition": {
            "enabled": true,
            "defaultType": "fade",
            "duration": 0.5
        },
        "rateLimit": {
            "requestsPerMinute": 5,
            "requestsPerDay": 500
        }
    }'::jsonb,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    version = EXCLUDED.version,
    description = EXCLUDED.description,
    config = EXCLUDED.config,
    updated_at = CURRENT_TIMESTAMP;

-- ============================================================
-- 验证查询
-- ============================================================
-- 查询新添加的执行器配置
SELECT
    id,
    code,
    name,
    version,
    target_type,
    status,
    config->>'routingStrategy' as routing_strategy,
    created_at
FROM aigc_execution_binding
WHERE code IN (
    'digital_human_video_generation',
    'aliyun_video_generation',
    'hyperframes_integration'
)
ORDER BY code;

-- 统计执行器数量
SELECT
    target_type,
    status,
    COUNT(*) as count
FROM aigc_execution_binding
WHERE code LIKE '%video%' OR code LIKE '%hyperframes%'
GROUP BY target_type, status;
