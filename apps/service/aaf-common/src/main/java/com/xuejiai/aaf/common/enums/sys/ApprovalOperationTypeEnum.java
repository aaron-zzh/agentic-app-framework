package com.xuejiai.aaf.common.enums.sys;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 审批操作类型枚举，对应字典 approval_operation_type。 */
@Getter
@AllArgsConstructor
public enum ApprovalOperationTypeEnum implements ArrayValuable<String> {
    APPROVE("APPROVE", "通过"),
    REJECT("REJECT", "拒绝"),
    DELEGATE("DELEGATE", "委派"),
    ADD_SIGN("ADD_SIGN", "加签"),
    TRANSFER("TRANSFER", "转办"),
    WITHDRAW("WITHDRAW", "撤回"),
    URGE("URGE", "催办");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(ApprovalOperationTypeEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
