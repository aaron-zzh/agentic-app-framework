package com.xuejiai.aaf.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 助理可自动执行的最高风险等级（用于 PermissionScope 配置）。 */
@Getter
@AllArgsConstructor
public enum RiskLevel {
    LOW("仅只读操作"),
    MEDIUM("低风险读写"),
    HIGH("中高风险操作");

    private final String description;
}
