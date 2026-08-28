package com.xuejiai.aaf.framework.intelligent.core.skill;

import java.util.Objects;

/**
 * Skill 版本挂载的参考文档定义。
 *
 * <p>对应 {@code ai_skill_reference}。文档权限与技能正文同级信任：技能被授权使用即代表其挂载的参考文档可读，不对当前
 * 调用者单独校验文档可见性——参考文档是技能定义的延伸部分，不是调用者自己的资料。
 */
public record SkillReference(
        String referenceKey,
        String title,
        Long documentId,
        Long documentVersionId,
        boolean required,
        int maxTokens) {

    public SkillReference {
        referenceKey = requireText(referenceKey, "referenceKey");
        title = requireText(title, "title");
        Objects.requireNonNull(documentId, "documentId 不能为空");
        Objects.requireNonNull(documentVersionId, "documentVersionId 不能为空");
        if (maxTokens < 1) {
            throw new IllegalArgumentException("maxTokens 必须大于 0");
        }
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " 不能为空");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空白");
        }
        return value.trim();
    }
}
