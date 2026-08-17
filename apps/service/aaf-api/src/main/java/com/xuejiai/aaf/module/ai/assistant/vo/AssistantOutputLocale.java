package com.xuejiai.aaf.module.ai.assistant.vo;

/** 通用 Assistant 输出语言稳定编码。 */
public enum AssistantOutputLocale {
    EN("en"),
    JA("ja"),
    KO("ko"),
    FR("fr"),
    ES("es");

    private final String languageTag;

    AssistantOutputLocale(String languageTag) {
        this.languageTag = languageTag;
    }

    /** 返回注入输出提示的 BCP 47 语言标签。 */
    public String languageTag() {
        return languageTag;
    }
}
