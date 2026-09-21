package com.xuejiai.aaf.common.enums.aigc;

import java.util.Arrays;
import java.util.Set;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** AIGC 任务状态枚举，对应字典 aigc_task_status。 */
@Getter
@AllArgsConstructor
public enum AigcTaskStatusEnum implements ArrayValuable<String> {
    PREPARED("PREPARED", "已准备"),
    SUBMITTING("SUBMITTING", "提交中"),
    NEEDS_RECONCILIATION("NEEDS_RECONCILIATION", "待对账"),
    PENDING("PENDING", "等待中"),
    RUNNING("RUNNING", "运行中"),
    COMPLETING("COMPLETING", "完成中"),
    SUCCESS("SUCCESS", "成功"),
    FAIL("FAIL", "失败");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(AigcTaskStatusEnum::getCode).toArray(String[]::new);

    /**
     * 占用用户并发窗口槽位的状态编码集合。
     *
     * <p>{@code PREPARED} 只表示已受理并排队，尚未提交供应商，因此不占槽；{@code NEEDS_RECONCILIATION}
     * 的供应商侧结果不确定，必须继续占槽直到人工对账， 否则会在真实任务仍可能在跑时放行新任务。并发槽位语义唯一定义在本枚举，调用方不得另行推导。
     */
    public static final Set<String> CONCURRENCY_OCCUPYING_CODES =
            Set.of(
                    SUBMITTING.code,
                    NEEDS_RECONCILIATION.code,
                    PENDING.code,
                    RUNNING.code,
                    COMPLETING.code);

    /** 已释放并发窗口槽位的状态编码集合，仅成功与失败两个终态。 */
    public static final Set<String> CONCURRENCY_RELEASING_CODES = Set.of(SUCCESS.code, FAIL.code);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
