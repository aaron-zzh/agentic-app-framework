package com.xuejiai.aaf.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** AI 助理超出权限边界时的处理策略。 */
@Getter
@AllArgsConstructor
public enum OverLimitAction {
    ASK("向委托者实时申请"),
    SKIP("跳过该操作，继续后续任务"),
    PAUSE("暂停整个任务，等待用户介入");

    private final String description;
}
