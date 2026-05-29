package com.xuejiai.aaf.module.ai.chat.domain.enums;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 任务执行实例状态。 */
@Getter
@AllArgsConstructor
public enum TaskExecutionStatus implements ArrayValuable<Integer> {
    PENDING(0, "等待执行"),
    RUNNING(1, "执行中"),
    DONE(2, "已完成"),
    FAILED(3, "失败"),
    CANCELLED(4, "已取消"),
    WAITING_APPROVAL(5, "等待人工审批");

    private final Integer status;
    private final String name;

    public static final Integer[] ARRAYS =
            Arrays.stream(values()).map(TaskExecutionStatus::getStatus).toArray(Integer[]::new);

    @Override
    public Integer[] array() {
        return ARRAYS;
    }
}
