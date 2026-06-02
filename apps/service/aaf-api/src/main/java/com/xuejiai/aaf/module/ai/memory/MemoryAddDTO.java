/**
 * 记忆写入请求。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.module.ai.memory;

/**
 * 显式"记住"一条记忆（对齐 m_flow add）。
 *
 * @param content 记忆内容（必填）
 * @param scope 范围：short_term/long_term/episodic/procedural，默认 long_term
 */
public record MemoryAddDTO(String content, String scope) {}
