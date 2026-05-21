/**
 * 记忆消息。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.framework.intelligent.cognition.memory;

import java.time.Instant;

/** 记忆系统中的消息单元。 */
public record MemoryMessage(String role, String content, Instant timestamp) {

    public MemoryMessage(String role, String content) {
        this(role, content, Instant.now());
    }
}
