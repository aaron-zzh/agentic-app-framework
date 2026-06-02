/**
 * 工作记忆接口——Agent 任务级注意焦点（对齐认知心理学工作记忆模型）。
 *
 * <p>工作记忆 ≠ 短期记忆。工作记忆是 Agent 当前任务的"注意焦点"（3-7 项）， 从长期记忆/知识库中主动提取最相关信息，组装为 LLM prompt 的一部分。
 *
 * <p>特性：有限容量、任务级生命周期、不持久化。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.agent.runtime;

import java.util.List;

/** Agent 工作记忆：管理当前任务的注意焦点。 混合检索时实现——从记忆系统 + 知识库检索后，选择最相关的 N 项放入焦点。 */
public interface WorkingMemory {

    /**
     * 从记忆/知识中提取焦点项放入工作记忆。
     *
     * @param agentId Agent 实例标识
     * @param query 当前任务/查询
     * @param maxItems 最大焦点项数（认知心理学：3-7 项）
     */
    void focus(String agentId, String query, int maxItems);

    /**
     * 获取当前工作记忆中的焦点项（用于组装 prompt）。
     *
     * @param agentId Agent 实例标识
     * @return 焦点项列表（已按相关性排序）
     */
    List<FocusItem> getFocus(String agentId);

    /**
     * 向工作记忆追加一项（Agent 执行中产生的中间结果）。
     *
     * @param agentId Agent 实例标识
     * @param item 新焦点项
     */
    void append(String agentId, FocusItem item);

    /**
     * 释放工作记忆（任务结束时调用）。
     *
     * @param agentId Agent 实例标识
     */
    void release(String agentId);

    /** 焦点项：工作记忆中的一个条目 */
    record FocusItem(
            /** 来源类型：memory / knowledge / tool_result / intermediate */
            String source,
            /** 内容 */
            String content,
            /** 相关性分数 */
            double relevance) {}
}
