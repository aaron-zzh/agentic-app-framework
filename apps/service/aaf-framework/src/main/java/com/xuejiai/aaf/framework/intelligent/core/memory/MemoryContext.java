package com.xuejiai.aaf.framework.intelligent.core.memory;

/** 记忆管道输出——各 Stage 格式化后的上下文块，可直接注入 Prompt。 */
public record MemoryContext(
        String shortTermBlock, // 近期对话（Redis 短期记忆）
        String longTermBlock, // 相关记忆摘要（语义检索）
        String proceduralBlock, // 经验 SOP（程序化记忆）
        String knowledgeBlock, // 知识库片段（混合检索）
        int totalTokens // 已用 Token 数（供 Core 层预算控制）
        ) {
    /** 组装为 Prompt 片段，过滤空块 */
    public String toPromptSection() {
        var sb = new StringBuilder();
        append(sb, "近期对话", shortTermBlock);
        append(sb, "相关记忆", longTermBlock);
        append(sb, "经验参考", proceduralBlock);
        append(sb, "知识库", knowledgeBlock);
        return sb.toString().trim();
    }

    private void append(StringBuilder sb, String label, String block) {
        if (block != null && !block.isBlank()) {
            sb.append("## ").append(label).append("\n").append(block).append("\n\n");
        }
    }

    public static MemoryContext empty() {
        return new MemoryContext(null, null, null, null, 0);
    }
}
