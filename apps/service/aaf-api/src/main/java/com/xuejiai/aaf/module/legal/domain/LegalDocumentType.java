package com.xuejiai.aaf.module.legal.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * 法律文档类型枚举。
 *
 * <p>与 {@code doc_document.doc_type} 字段值对齐。当用户登录时，需要分别检查这两类文档是否存在用户未同意的最新已发布版本。
 *
 * @author AaronZZH &amp; Kiro
 */
public enum LegalDocumentType {

    /** 服务条款（用户协议） */
    LEGAL_TERMS("legal-terms", "服务条款"),

    /** 隐私政策 */
    LEGAL_PRIVACY("legal-privacy", "隐私政策");

    private final String code;
    private final String displayName;

    LegalDocumentType(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** 通过 doc_type 字符串解析枚举，找不到时返回空。 */
    public static Optional<LegalDocumentType> fromCode(String code) {
        if (code == null) return Optional.empty();
        return Arrays.stream(values()).filter(t -> t.code.equals(code)).findFirst();
    }
}
