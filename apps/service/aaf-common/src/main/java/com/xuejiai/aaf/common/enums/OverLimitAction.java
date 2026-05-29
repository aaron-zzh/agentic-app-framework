package com.xuejiai.aaf.common.enums;

import java.util.Arrays;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** AI 助理超出权限边界时的处理策略。 */
@Getter
@AllArgsConstructor
public enum OverLimitAction implements ArrayValuable<String> {
    ASK("ask", "向委托者实时申请"),
    SKIP("skip", "跳过该操作，继续后续任务"),
    PAUSE("pause", "暂停整个任务，等待用户介入");

    private final String code;
    private final String description;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(OverLimitAction::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
