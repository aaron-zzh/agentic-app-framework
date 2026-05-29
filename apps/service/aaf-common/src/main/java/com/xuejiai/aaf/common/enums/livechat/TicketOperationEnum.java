package com.xuejiai.aaf.common.enums.livechat;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 工单操作类型枚举。 */
@Getter
@AllArgsConstructor
public enum TicketOperationEnum implements ArrayValuable<String> {
    CREATE("create", "创建"),
    ASSIGN("assign", "分配"),
    START("start", "开始处理"),
    CONFIRM("confirm", "提交确认"),
    CLOSE("close", "关闭"),
    REOPEN("reopen", "重新打开"),
    TRANSFER("transfer", "转派");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(TicketOperationEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
