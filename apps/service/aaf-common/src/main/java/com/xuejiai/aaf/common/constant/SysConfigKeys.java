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

        public static final String DEFAULT_PASSWORD = "user.default_password"; // 用户默认密码
        public static final String REGISTER_ENABLED = "user.register_enabled"; // 是否开放注册
        public static final String LOGIN_FAIL_LOCK_COUNT = "user.login_fail_lock_count"; // 登录失败锁定次数
        public static final String LOGIN_FAIL_LOCK_MINUTES =
                "user.login_fail_lock_minutes"; // 账号锁定时长（分钟）
    }

    /** 安全配置 */
    public static final class Security {

        private Security() {}

        public static final String CAPTCHA_ENABLED = "security.captcha_enabled"; // 是否启用验证码
        public static final String VERIFY_CODE_EXPIRE = "security.verify_code_expire"; // 验证码有效期（分钟）
    }

    /** 存储配置 */
    public static final class Storage {

        private Storage() {}

        public static final String UPLOAD_MAX_SIZE_MB =
                "storage.upload_max_size_mb"; // 文件上传大小限制（MB）
        public static final String ALLOWED_TYPES = "storage.allowed_types"; // 允许上传的文件类型
    }

    /** AI 配置 */
    public static final class Ai {

        private Ai() {}

        public static final String DEFAULT_MODEL = "ai.default_model"; // AI 默认模型
        public static final String TOKEN_QUOTA_PER_USER =
                "ai.token_quota_per_user"; // 用户 Token 配额（每月）
        public static final String CREDIT_WARN_THRESHOLD =
                "ai.credit_warn_threshold"; // 积分预警阈值，低于此值发预警通知
        public static final String CREDIT_OVERDRAFT_LIMIT =
                "ai.credit_overdraft_limit"; // 积分透支上限（token 场景允许欠费，默认 0 即不允许）
        public static final String FREE_ASSISTANT_CREDIT_CAP =
                "ai.free_assistant_credit_cap"; // 免费助理虚拟用户预算上限（积分）
        public static final String TOKEN_MARKUP_RATE =
                "ai.token_markup_rate"; // Token 计费加价倍数（相对供应商成本，默认10倍）
    }

    /** 品牌配置 */
    public static final class Brand {

        private Brand() {}

        public static final String COMPANY_NAME = "brand.company_name"; // 公司名称
        public static final String LOGO_URL = "brand.logo_url"; // Logo URL
    }

    /** 会员与积分配置 */
    public static final class Member {

        private Member() {}

        public static final String MONTHLY_GRANT_ENABLED =
                "member.monthly_grant_enabled"; // 订阅月度积分发放开关
        public static final String WEEKLY_GRANT_ENABLED = "member.weekly_grant_enabled"; // 每周积分发放开关
        public static final String CREDIT_EXPIRE_ENABLED =
                "member.credit_expire_enabled"; // 积分过期清理开关
        public static final String FAQ = "member.faq"; // 会员与积分常见问题（JSON 数组）
    }

    /** AIGC 通用配置 */
    public static final class Aigc {

        private Aigc() {}

        /** AIGC 生成 Mock 开关，开启后跳过真实 API 调用，返回 {@link #MOCK_DATA} 中的固定值，适用于开发调试。 */
        public static final String MOCK_ENABLED = "aigc.mock_enabled";

        /** AIGC Mock 固定返回数据（JSON），各类型 key 对应固定返回值。示例： */
        public static final String MOCK_DATA = "aigc.mock_data";
    }

    /** 示例模块配置 */
    public static final class Examples {

        private Examples() {}

        public static final String AGENTSCOPE_RATE_LIMIT_PER_MINUTE =
                "examples.agentscope_rate_limit_per_minute"; // AgentScope 示例接口限流（次/分钟/IP）
    }
}
