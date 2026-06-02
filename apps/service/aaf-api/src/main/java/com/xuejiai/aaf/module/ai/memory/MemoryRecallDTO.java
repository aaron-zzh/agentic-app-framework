/**
 * 记忆语义检索请求。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.module.ai.memory;

/**
 * 语义检索记忆（对齐 m_flow search：返回相关上下文，不做 LLM 答案合成）。
 *
 * @param query 自然语言查询（必填）
 * @param topK 返回数量，默认 8
 */
public record MemoryRecallDTO(String query, Integer topK) {}
