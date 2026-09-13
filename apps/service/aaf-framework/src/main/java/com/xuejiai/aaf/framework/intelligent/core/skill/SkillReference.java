package com.xuejiai.aaf.framework.intelligent.core.skill;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * SkillVersion 挂载的参考文档及其加载策略。
 *
 * <p>对应 {@code ai_skill_reference}。文档权限与技能正文同级信任：技能发布时应保证引用内容可作为执行资产读取；运行时不再对调用者单独校验文档可见性。
 */
public record SkillReference(
        String referenceKey,
        String title,
        Long documentId,
        Long documentVersionId,
        String selector,
        LoadMode loadMode,
        String loadWhen,
        boolean required,
        int maxTokens,
        int sortOrder,
        String contentHash) {

    public SkillReference {
        referenceKey = requireText(referenceKey, "referenceKey");
        title = requireText(title, "title");
        Objects.requireNonNull(documentId, "documentId 不能为空");
        Objects.requireNonNull(documentVersionId, "documentVersionId 不能为空");
        selector = blankToNull(selector);
        Objects.requireNonNull(loadMode, "loadMode 不能为空");
        loadWhen = requireText(loadWhen, "loadWhen");
        if (maxTokens < 1) {
            throw new IllegalArgumentException("maxTokens 必须大于 0");
        }
        if (sortOrder < 0) {
            throw new IllegalArgumentException("sortOrder 不能小于 0");
        }
        contentHash = blankToNull(contentHash);
    }

    /** 生成绑定不可变 SkillVersion 的全局稳定加载键。 */
    public String loadKey(String skillCode, Long skillVersionId) {
        var code = requireText(skillCode, "skillCode");
        Objects.requireNonNull(skillVersionId, "skillVersionId 不能为空");
        if (skillVersionId < 1) {
            throw new IllegalArgumentException("skillVersionId 必须大于 0");
        }
        if (code.indexOf('@') >= 0 || code.indexOf(':') >= 0 || referenceKey.indexOf(':') >= 0) {
            throw new IllegalArgumentException("Skill 或 Reference code 包含 loadKey 保留字符");
        }
        return "%s@%d:%s".formatted(code, skillVersionId, referenceKey);
    }

    /** 校验参考正文仍满足已发布引用的完整性和上下文预算。 */
    public String requireLoadableContent(String content) {
        Objects.requireNonNull(content, "referenceContent 不能为空");
        if (content.isBlank()) {
            throw new IllegalArgumentException("referenceContent 不能为空白");
        }
        var value = content;
        if (selector != null) {
            throw new IllegalStateException("Skill Reference selector 尚未定义运行时合同: " + selector);
        }
        if (contentHash == null || !contentHash.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalStateException(
                    "Skill Reference 缺少合法 SHA-256 contentHash: " + referenceKey);
        }
        if (!contentHash.equalsIgnoreCase(sha256(value))) {
            throw new IllegalStateException("Skill Reference 内容哈希不一致: " + referenceKey);
        }
        var estimatedTokens = Math.max(1, (value.codePointCount(0, value.length()) + 3) / 4);
        if (estimatedTokens > maxTokens) {
            throw new IllegalStateException(
                    "Skill Reference 超出 maxTokens: %s，estimated=%d，max=%d"
                            .formatted(referenceKey, estimatedTokens, maxTokens));
        }
        return value;
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " 不能为空");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空白");
        }
        return value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("运行环境不支持 SHA-256", failure);
        }
    }

    public enum LoadMode {
        ON_ACTIVATION,
        ON_DEMAND
    }
}
