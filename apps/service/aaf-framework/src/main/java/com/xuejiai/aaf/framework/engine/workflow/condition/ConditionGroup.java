package com.xuejiai.aaf.framework.engine.workflow.condition;

import java.util.List;

/**
 * 条件组——支持嵌套的条件组合。
 *
 * @param logic 组内条件的逻辑关系
 * @param conditions 条件列表
 * @param groups 嵌套子组
 */
public record ConditionGroup(
        ConditionExpression.Logic logic,
        List<ConditionExpression> conditions,
        List<ConditionGroup> groups) {}
