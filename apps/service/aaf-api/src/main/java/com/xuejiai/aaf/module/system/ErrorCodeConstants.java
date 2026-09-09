package com.xuejiai.aaf.module.system;

import com.xuejiai.aaf.common.exception.ErrorCode;

/**
 * System 模块错误码，使用 1_000_000 ~ 1_999_999 段。
 *
 * <p>子模块分段：
 *
 * <ul>
 *   <li>AUTH：1_000_000 ~ 1_000_999
 *   <li>USER：1_001_000 ~ 1_001_999
 *   <li>MENU：1_002_000 ~ 1_002_999
 *   <li>CHAT：1_003_000 ~ 1_003_999
 *   <li>AI MODEL：1_004_000 ~ 1_004_999
 *   <li>DICT：1_005_000 ~ 1_005_999
 *   <li>TODO：1_006_000 ~ 1_006_999
 *   <li>ORG：1_007_000 ~ 1_007_999
 *   <li>DASHBOARD：1_008_000 ~ 1_008_999
 *   <li>ENTITY：1_009_000 ~ 1_009_999
 *   <li>LICENSE：1_010_000 ~ 1_010_999
 *   <li>ROLE_PERMISSION：1_011_000 ~ 1_011_999
 *   <li>NOTIFY：1_012_000 ~ 1_012_999
 *   <li>LOG：1_013_000 ~ 1_013_999
 *   <li>FILE：1_014_000 ~ 1_014_999
 *   <li>PROFILE：1_015_000 ~ 1_015_999
 * </ul>
 */
public interface ErrorCodeConstants {

    // ========== AUTH 模块 1_000_000 ==========
    ErrorCode AUTH_LOGIN_BAD_CREDENTIALS = ErrorCode.of(1_000_000, "登录失败，账号密码不正确");
    ErrorCode AUTH_LOGIN_USER_DISABLED = ErrorCode.of(1_000_001, "登录失败，账号被禁用");
    ErrorCode AUTH_TOKEN_EXPIRED = ErrorCode.of(1_000_002, "Token 无效或已过期");
    ErrorCode AUTH_EMAIL_ALREADY_REGISTERED = ErrorCode.of(1_000_003, "该邮箱已注册");
    ErrorCode AUTH_VERIFY_CODE_INVALID = ErrorCode.of(1_000_004, "验证码无效或已过期");
    ErrorCode AUTH_VERIFY_CODE_RATE_LIMIT = ErrorCode.of(1_000_005, "发送过于频繁，请稍后再试");
    ErrorCode AUTH_USER_LOCKED = ErrorCode.of(1_000_006, "账号已锁定，请稍后再试");
    ErrorCode AUTH_EMAIL_NOT_VERIFIED = ErrorCode.of(1_000_007, "邮箱未验证");
    ErrorCode AUTH_REGISTER_IP_RATE_LIMIT = ErrorCode.of(1_000_009, "注册过于频繁，请稍后再试");
    ErrorCode AUTH_VERIFY_CODE_SEND_FAILED = ErrorCode.of(1_000_010, "验证码发送失败，请稍后重试");
    ErrorCode AUTH_EMAIL_NOT_REGISTERED = ErrorCode.of(1_000_011, "该邮箱尚未注册");

    // ========== USER 模块 1_001_000 ==========
    ErrorCode USER_NOT_FOUND = ErrorCode.of(1_001_000, "用户不存在");
    ErrorCode USER_USERNAME_EXISTS = ErrorCode.of(1_001_001, "用户名已存在");
    ErrorCode USER_PASSWORD_INCORRECT = ErrorCode.of(1_001_002, "旧密码不正确");
    ErrorCode USER_ADMIN_DELETE_FORBIDDEN = ErrorCode.of(1_001_003, "不允许删除管理员");
    ErrorCode USER_PHONE_ALREADY_BOUND = ErrorCode.of(1_001_004, "该手机号已被其他账户绑定");
    ErrorCode USER_FAVORITE_NOT_FOUND = ErrorCode.of(1_001_005, "收藏不存在");

    // ========== MENU 模块 1_002_000 ==========
    ErrorCode MENU_NOT_FOUND = ErrorCode.of(1_002_000, "菜单不存在");

    // ========== OAUTH 模块 1_000_100 ==========
    ErrorCode OAUTH_PROVIDER_NOT_CONFIGURED = ErrorCode.of(1_000_100, "该 OAuth 提供商未配置");
    ErrorCode OAUTH_EXCHANGE_FAILED = ErrorCode.of(1_000_101, "OAuth 授权码换取用户信息失败");
    ErrorCode OAUTH_ALREADY_BOUND = ErrorCode.of(1_000_102, "该第三方账号已绑定其他用户");
    ErrorCode OAUTH_NOT_BOUND = ErrorCode.of(1_000_103, "未绑定该第三方账号");

    // ========== CHAT 模块 1_003_000 ==========
    ErrorCode CHAT_SESSION_NOT_FOUND = ErrorCode.of(1_003_000, "聊天会话不存在");
    ErrorCode CHAT_MESSAGE_NOT_FOUND = ErrorCode.of(1_003_001, "聊天消息不存在");

    // ========== AI MODEL 模块 1_004_000 ==========
    ErrorCode AI_MODEL_NOT_FOUND = ErrorCode.of(1_004_000, "AI 模型不存在");
    ErrorCode AI_MODEL_ID_EXISTS = ErrorCode.of(1_004_001, "模型 ID 已存在");
    ErrorCode AI_MODEL_IMPORT_INVALID = ErrorCode.of(1_004_004, "模型导入文件格式不正确");

    // ========== DICT 模块 1_005_000 ==========
    ErrorCode DICT_TYPE_NOT_FOUND = ErrorCode.of(1_005_000, "字典类型不存在");
    ErrorCode DICT_TYPE_CODE_EXISTS = ErrorCode.of(1_005_001, "字典类型编码已存在");
    ErrorCode DICT_TYPE_NAME_EXISTS = ErrorCode.of(1_005_002, "字典名称已存在");
    ErrorCode DICT_TYPE_HAS_DATA = ErrorCode.of(1_005_003, "该字典类型下存在字典数据，请先删除");

    // ========== TODO 模块 1_006_000 ==========
    ErrorCode TODO_NOT_FOUND = ErrorCode.of(1_006_000, 404, "待办不存在");
    ErrorCode TODO_SOURCE_RESOURCE_NOT_FOUND = ErrorCode.of(1_006_001, 404, "待办来源记录不存在");
    ErrorCode TODO_SHARE_COMMAND_INVALID = ErrorCode.of(1_006_002, "待办分享命令不合法");
    ErrorCode TODO_SOURCE_TYPE_REQUIRED = ErrorCode.of(1_006_003, "待办来源类型不能为空");
    ErrorCode TODO_SOURCE_UPDATE_FORBIDDEN = ErrorCode.of(1_006_004, "当前待办来源不可修改");
    ErrorCode TODO_ASSIGNEE_REQUIRED = ErrorCode.of(1_006_005, "待办执行人不能为空");
    ErrorCode TODO_SOURCE_REFERENCE_INVALID = ErrorCode.of(1_006_006, "待办来源引用不合法");
    ErrorCode TODO_VERSION_CONFLICT = ErrorCode.of(1_006_007, 409, "待办已被其他请求修改，请刷新后重试");
    ErrorCode TODO_TITLE_REQUIRED = ErrorCode.of(1_006_008, "待办标题不能为空");
    ErrorCode TODO_CATEGORY_INVALID = ErrorCode.of(1_006_009, "待办分类不合法");
    ErrorCode TODO_STATUS_INVALID = ErrorCode.of(1_006_010, "待办状态不合法");
    ErrorCode TODO_QUEUE_PAYLOAD_INVALID = ErrorCode.of(1_006_011, "待办清理任务载荷不合法");

    // ========== ORG 模块 1_007_000 ==========
    ErrorCode ORG_NOT_FOUND = ErrorCode.of(1_007_000, "组织不存在");
    ErrorCode ORG_SLUG_EXISTS = ErrorCode.of(1_007_001, "组织标识已存在");
    ErrorCode ORG_PERSONAL_DELETE_FORBIDDEN = ErrorCode.of(1_007_002, "个人组织不可删除");
    ErrorCode ORG_MEMBER_ALREADY_EXISTS = ErrorCode.of(1_007_003, "用户已是组织成员");
    ErrorCode ORG_MEMBER_NOT_FOUND = ErrorCode.of(1_007_004, "成员不存在");
    ErrorCode ORG_OWNER_ROLE_CHANGE_FORBIDDEN = ErrorCode.of(1_007_005, "不能修改所有者角色");
    ErrorCode ORG_OWNER_REMOVE_FORBIDDEN = ErrorCode.of(1_007_006, "不能移除组织所有者");
    ErrorCode WORKSPACE_ORG_CONTEXT_REQUIRED = ErrorCode.of(1_007_007, "缺少组织上下文，无法创建工作区");
    ErrorCode WORKSPACE_SLUG_EXISTS = ErrorCode.of(1_007_008, "工作区标识已存在");
    ErrorCode WORKSPACE_MEMBER_ALREADY_EXISTS = ErrorCode.of(1_007_009, "用户已是工作区成员");
    ErrorCode WORKSPACE_MEMBER_NOT_FOUND = ErrorCode.of(1_007_010, "成员不存在");
    ErrorCode WORKSPACE_MANAGER_REMOVE_FORBIDDEN = ErrorCode.of(1_007_011, "不能移除工作区管理者");
    ErrorCode WORKSPACE_MANAGER_REQUIRED = ErrorCode.of(1_007_012, "仅工作区管理者可执行此操作");
    ErrorCode ORG_MEMBER_REQUIRED = ErrorCode.of(1_007_013, "您不是该组织成员");
    ErrorCode ORG_MANAGER_REQUIRED = ErrorCode.of(1_007_014, "仅组织所有者或管理员可执行此操作");
    ErrorCode WORKSPACE_MEMBER_ORG_REQUIRED = ErrorCode.of(1_007_015, "用户不是该工作区所属组织的成员");
    ErrorCode ORG_MEMBER_WORKSPACE_OWNER_REMOVE_FORBIDDEN =
            ErrorCode.of(1_007_016, "用户仍是工作区管理者，请先处理工作区归属");
    ErrorCode ORG_DEFAULT_CONTEXT_NOT_FOUND = ErrorCode.of(1_007_017, "当前用户的默认组织或工作区不存在");

    // ========== DASHBOARD 模块 1_008_000 ==========
    ErrorCode DASHBOARD_NOT_FOUND = ErrorCode.of(1_008_000, "仪表盘不存在");
    ErrorCode DASHBOARD_WIDGET_CONFIG_REQUIRED = ErrorCode.of(1_008_001, "缺少组件 config");
    ErrorCode DASHBOARD_WIDGET_TYPE_REQUIRED = ErrorCode.of(1_008_002, "config.type 缺失");
    ErrorCode DASHBOARD_PRESET_WIDGETS_PARSE_FAILED =
            ErrorCode.of(1_008_003, "预设 widgets 解析失败: id={0}");
    ErrorCode DASHBOARD_WIDGET_TYPE_UNKNOWN = ErrorCode.of(1_008_004, "未知组件类型: {0}");
    ErrorCode DASHBOARD_BILLING_COMPONENT_REQUIRED =
            ErrorCode.of(1_008_005, "billing widget 缺少 component");
    ErrorCode DASHBOARD_BILLING_COMPONENT_UNKNOWN = ErrorCode.of(1_008_006, "未知 billing 组件: {0}");
    ErrorCode DASHBOARD_PRESET_METRIC_UNKNOWN = ErrorCode.of(1_008_007, "未知预定义指标: {0}");
    ErrorCode DASHBOARD_LIST_COLUMNS_REQUIRED = ErrorCode.of(1_008_008, "list 组件缺少 columns");
    ErrorCode DASHBOARD_IDENTIFIER_INVALID = ErrorCode.of(1_008_009, "非法标识符: {0}");
    ErrorCode DASHBOARD_WIDGET_POSITION_PARSE_FAILED =
            ErrorCode.of(1_008_010, "widget position 解析失败: id={0}");
    ErrorCode DASHBOARD_WIDGET_CONFIG_PARSE_FAILED =
            ErrorCode.of(1_008_011, "widget config 解析失败: id={0}");
    ErrorCode PAGE_DEF_PUBLISHED_NOT_FOUND = ErrorCode.of(1_008_012, "页面未找到: {0}");
    ErrorCode PAGE_DEF_SLUG_EXISTS = ErrorCode.of(1_008_013, "slug 已存在: {0}");
    ErrorCode PAGE_DEF_NOT_FOUND = ErrorCode.of(1_008_014, "页面定义不存在: {0}");

    // ========== ENTITY 模块 1_009_000 ==========
    ErrorCode ENTITY_DEF_SLUG_EXISTS = ErrorCode.of(1_009_000, "slug 已存在: {0}");
    ErrorCode ENTITY_DEF_BUILTIN_UPDATE_FORBIDDEN = ErrorCode.of(1_009_001, "内置实体定义不可修改");
    ErrorCode ENTITY_DEF_BUILTIN_DELETE_FORBIDDEN = ErrorCode.of(1_009_002, "内置实体定义不可删除");
    ErrorCode ENTITY_DEF_CONFIG_INVALID = ErrorCode.of(1_009_003, "EntityDef config 必须是 JSON 对象");
    ErrorCode ENTITY_DEF_VIEW_FIELD_UNDECLARED = ErrorCode.of(1_009_004, "字段未在展示 VO 中声明: {0}");
    ErrorCode ENTITY_DEF_NOT_FOUND = ErrorCode.of(1_009_005, "实体定义不存在");
    ErrorCode RECORD_TEMPLATE_NOT_FOUND = ErrorCode.of(1_009_006, "模板不存在");

    // ========== LICENSE 模块 1_010_000 ==========
    ErrorCode LICENSE_SIGNING_KEY_NOT_CONFIGURED = ErrorCode.of(1_010_000, "未配置 license 签发私钥");
    ErrorCode LICENSE_ISSUE_FAILED = ErrorCode.of(1_010_001, "license 签发失败，请检查私钥配置");
    ErrorCode LICENSE_FEATURES_INVALID = ErrorCode.of(1_010_002, "features 只能包含已登记的高级模块：{0}");
    ErrorCode LICENSE_SUBJECT_INVALID = ErrorCode.of(1_010_003, "license user_id 格式不合法，请留空自动生成");
    ErrorCode LICENSE_SOURCE_ARCHIVE_PATH_NOT_CONFIGURED = ErrorCode.of(1_010_004, "未配置源码包路径");
    ErrorCode LICENSE_SOURCE_ARCHIVE_NOT_FOUND = ErrorCode.of(1_010_005, "源码包不存在");

    // ========== ROLE_PERMISSION 模块 1_011_000 ==========
    ErrorCode ROLE_NOT_FOUND = ErrorCode.of(1_011_000, "角色不存在");
    ErrorCode ROLE_CODE_EXISTS = ErrorCode.of(1_011_001, "角色编码已存在");
    ErrorCode PERMISSION_CODE_EXISTS = ErrorCode.of(1_011_002, "权限编码已存在");
    ErrorCode PERMISSION_CODE_NOT_FOUND = ErrorCode.of(1_011_003, "权限码不存在");
    ErrorCode PERMISSION_SEGMENT_REQUIRED = ErrorCode.of(1_011_004, "权限码分段不能为空");
    ErrorCode ACCESS_POLICY_NOT_FOUND = ErrorCode.of(1_011_005, "策略不存在");
    ErrorCode DATA_ACCESS_RULE_ENTITY_UNKNOWN =
            ErrorCode.of(1_011_006, "实体标识 {0} 未注册到 CRUD 资源目录，无法校验规则字段");
    ErrorCode DATA_ACCESS_RULE_FIELD_INVALID =
            ErrorCode.of(1_011_007, "规则条件引用了非法字段: {0}（实体 {1} 不存在该字段或不支持行级过滤）");
    ErrorCode DATA_ACCESS_RULE_OPERATOR_INVALID = ErrorCode.of(1_011_008, "规则条件使用了不支持的操作符: {0}");

    // ========== NOTIFY 模块 1_012_000 ==========
    ErrorCode SUBSCRIPTION_NOT_FOUND = ErrorCode.of(1_012_000, "订阅不存在");
    ErrorCode SUBSCRIPTION_CANCEL_FORBIDDEN = ErrorCode.of(1_012_001, "无权取消他人订阅");
    ErrorCode MESSAGE_TEMPLATE_NOT_FOUND = ErrorCode.of(1_012_002, "消息模板不存在");
    ErrorCode NOTICE_NOT_FOUND = ErrorCode.of(1_012_003, "公告不存在");

    // ========== LOG 模块 1_013_000 ==========
    ErrorCode COMMENT_NOT_FOUND = ErrorCode.of(1_013_000, "评论不存在");
    ErrorCode RECORD_VERSION_NOT_FOUND = ErrorCode.of(1_013_001, "版本不存在");

    // ========== FILE 模块 1_014_000 ==========
    ErrorCode FILE_CONFIG_NOT_FOUND = ErrorCode.of(1_014_000, "文件存储配置不存在");
    ErrorCode FILE_STORAGE_MASTER_NOT_FOUND = ErrorCode.of(1_014_001, "未配置可用的主文件存储");
    ErrorCode FILE_STORAGE_CONFIG_INVALID = ErrorCode.of(1_014_002, "文件存储配置无效: {0}");
    ErrorCode FILE_STORAGE_CONFIG_REFERENCED = ErrorCode.of(1_014_003, "文件存储配置已被引用，不能删除或修改存储位置");
    ErrorCode FILE_STORAGE_CONFIG_MASTER_DELETE_FORBIDDEN =
            ErrorCode.of(1_014_004, "主文件存储不能删除，请先切换主配置");
    ErrorCode FILE_STORAGE_ACCESS_FAILED = ErrorCode.of(1_014_006, "文件存储访问失败");
    ErrorCode FILE_NOT_FOUND = ErrorCode.of(1_014_007, "文件不存在或无权访问");
    ErrorCode FILE_STORAGE_PUBLIC_ASSET_NOT_FOUND =
            ErrorCode.of(1_014_008, "公开资产存储必须唯一配置为允许公开访问的 OSS");

    // ========== PROFILE 模块 1_015_000 ==========
    ErrorCode PROFILE_DIMENSION_CODE_EXISTS = ErrorCode.of(1_015_000, "维度编码已存在: {0}");
    ErrorCode PROFILE_DIMENSION_NOT_FOUND = ErrorCode.of(1_015_001, "维度不存在");

    // ========== SMS 模块 1_016_000 ==========
    ErrorCode SMS_TEMPLATE_NOT_FOUND = ErrorCode.of(1_016_000, "短信模板不存在");
}
