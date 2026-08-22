package com.xuejiai.aaf.framework.intelligent.assistant.model;

import java.util.Objects;

/** Assistant 已解析的人格快照，描述稳定的“我是谁”，不表示执行或审计主体。 */
public record PersonaSnapshot(
        String personaKey,
        int personaRevision,
        String name,
        String description,
        String personality,
        String speakingStyle,
        String instructions,
        String avatarRef) {

    public PersonaSnapshot {
        personaKey = requireText(personaKey, "personaKey");
        if (personaRevision < 0) {
            throw new IllegalArgumentException("personaRevision 不能小于 0");
        }
        name = requireText(name, "Persona name");
        description = requireText(description, "Persona description");
        personality = requireText(personality, "Persona personality");
        speakingStyle = requireText(speakingStyle, "Persona speakingStyle");
        instructions = requireText(instructions, "Persona instructions");
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
