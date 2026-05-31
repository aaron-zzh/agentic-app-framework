package com.xuejiai.aaf.framework.intelligent.action;

import java.util.Locale;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;

/** AI 可调用的标准实体动作。 */
public enum AiBusinessActionType {
    QUERY("query", "read"),
    DETAIL("detail", "read"),
    BATCH_READ("batchRead", "read"),
    OPTIONS("options", "read"),
    META("meta", "read"),
    CREATE("create", "create"),
    UPDATE("update", "update"),
    DELETE("delete", "delete"),
    BATCH_DELETE("batchDelete", "delete"),
    EXPORT("export", "export"),
    VALIDATE("validate", "create"),
    ARCHIVE("archive", "delete"),
    RESTORE("restore", "update");

    private final String action;
    private final String permissionAction;

    AiBusinessActionType(String action, String permissionAction) {
        this.action = action;
        this.permissionAction = permissionAction;
    }

    public String action() {
        return action;
    }

    public String permissionAction() {
        return permissionAction;
    }

    public static AiBusinessActionType from(String rawAction) {
        if (rawAction == null || rawAction.isBlank()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "AI 业务动作不能为空");
        }
        var normalized = rawAction.trim();
        var dotIndex = normalized.lastIndexOf('.');
        if (dotIndex >= 0) {
            normalized = normalized.substring(dotIndex + 1);
        }
        var key = normalized.replace("-", "").replace("_", "").toLowerCase(Locale.ROOT);
        for (var type : values()) {
            var candidate = type.action.replace("-", "").replace("_", "").toLowerCase(Locale.ROOT);
            if (candidate.equals(key)) {
                return type;
            }
        }
        throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "不支持的 AI 业务动作: " + rawAction);
    }
}
