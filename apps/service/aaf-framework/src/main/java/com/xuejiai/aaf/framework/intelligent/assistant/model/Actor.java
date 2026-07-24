package com.xuejiai.aaf.framework.intelligent.assistant.model;

import java.util.Objects;

/** Assistant 可复用的人格定义，描述“我是谁”。 */
public record Actor(
        String key,
        String name,
        String description,
        String personality,
        String speakingStyle,
        String instructions,
        String avatarRef) {

    public Actor {
        key = requireText(key, "Actor key");
        name = requireText(name, "Actor name");
        description = requireText(description, "Actor description");
        personality = requireText(personality, "Actor personality");
        speakingStyle = requireText(speakingStyle, "Actor speakingStyle");
        instructions = requireText(instructions, "Actor instructions");
        avatarRef = avatarRef == null ? "" : avatarRef.trim();
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " 不能为空");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空白");
        }
        return value;
    }
}
