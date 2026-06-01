package com.xuejiai.aaf.common.constant;

/**
 * 系统配置键常量。
 *
 * <p>每个内部类对应 sys_config.category，常量值与 sys_config.config_key 字段一致。
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * configService.getInteger(SysConfigKeys.Ai.CREDIT_WARN_THRESHOLD, 10);
 * configService.getBoolean(SysConfigKeys.Security.CAPTCHA_ENABLED, true);
 * }</pre>
 */
public final class SysConfigKeys {

    private SysConfigKeys() {}

    /** 用户配置 */
    public static final class User {

        private User() {}

        public static final String DEFAULT_PASSWORD      = "user.default_password";       // 用户默认密码
        public static final String REGISTER_ENABLED      = "user.register_enabled";        // 是否开放注册
        public static final String LOGIN_FAIL_LOCK_COUNT   = "user.login_fail_lock_count";   // 登录失败锁定次数
        public static final String LOGIN_FAIL_LOCK_MINUTES = "user.login_fail_lock_minutes"; // 账号锁定时长（分钟）
    }

    /** 安全配置 */
    public static final class Security {

        private Security() {}

        public static final String CAPTCHA_ENABLED      = "security.captcha_enabled";      // 是否启用验证码
        public static final String VERIFY_CODE_EXPIRE   = "security.verify_code_expire";   // 验证码有效期（分钟）
    }

    /** 存储配置 */
    public static final class Storage {

        private Storage() {}

        public static final String UPLOAD_MAX_SIZE_MB = "storage.upload_max_size_mb"; // 文件上传大小限制（MB）
        public static final String ALLOWED_TYPES      = "storage.allowed_types";      // 允许上传的文件类型
    }

    /** AI 配置 */
    public static final class Ai {

        private Ai() {}

        public static final String DEFAULT_MODEL            = "ai.default_model";             // AI 默认模型
        public static final String TOKEN_QUOTA_PER_USER     = "ai.token_quota_per_user";      // 用户 Token 配额（每月）
        public static final String CREDIT_WARN_THRESHOLD    = "ai.credit_warn_threshold";     // 积分预警阈值，低于此值发预警通知
        public static final String FREE_ASSISTANT_CREDIT_CAP = "ai.free_assistant_credit_cap"; // 免费助理虚拟用户预算上限（积分）
        public static final String CONTEXT_ENABLED = "ai.context.enabled"; // 是否启用输入前上下文压缩
        public static final String CONTEXT_DEFAULT_POLICY = "ai.context.default_policy"; // 默认上下文策略
        public static final String CONTEXT_DEFAULT_WINDOW = "ai.context.default_context_window"; // 默认上下文窗口
        public static final String CONTEXT_RESERVED_OUTPUT_TOKENS = "ai.context.reserved_output_tokens"; // 输出预留 Token
        public static final String CONTEXT_FIXED_PROMPT_BUDGET = "ai.context.fixed_prompt_budget"; // 固定提示词预算
        public static final String CONTEXT_TRIGGER_RATIO = "ai.context.compression_trigger_ratio"; // 压缩触发比例
        public static final String CONTEXT_LAST_KEEP = "ai.context.last_keep"; // 最近保留消息数
        public static final String CONTEXT_MESSAGE_THRESHOLD = "ai.context.message_threshold"; // 消息数阈值
        public static final String CONTEXT_LARGE_INPUT_THRESHOLD = "ai.context.large_input_char_threshold"; // 大消息阈值
        public static final String CONTEXT_RULE_PREVIEW_CHARS = "ai.context.rule_preview_chars"; // 规则裁剪预览长度
        public static final String CONTEXT_ENABLE_SUMMARY = "ai.context.enable_summary"; // 是否启用摘要压缩
        public static final String CONTEXT_SUMMARY_MODEL_ID = "ai.context.summary_model_id"; // 摘要模型
        public static final String CONTEXT_SUMMARY_TIMEOUT_MS = "ai.context.summary_timeout_ms"; // 摘要超时
        public static final String CONTEXT_SUMMARY_SYSTEM_PROMPT = "ai.context.summary_system_prompt"; // 摘要系统提示词
        public static final String CONTEXT_SUMMARY_USER_PROMPT = "ai.context.summary_user_prompt"; // 摘要用户提示词模板
    }

    /** 品牌配置 */
    public static final class Brand {

        private Brand() {}

        public static final String COMPANY_NAME = "brand.company_name"; // 公司名称
        public static final String LOGO_URL     = "brand.logo_url";     // Logo URL
    }
}
