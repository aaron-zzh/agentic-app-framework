package com.xuejiai.aaf.framework.engine.knowledge.graph;

import java.util.List;

/** LLM 对某个候选组的终审合并决策。{@code mergeIds} 为空或少于 2 个元素表示该组不合并。 */
public record EntityMergeDecision(int groupIndex, List<String> mergeIds) {}
