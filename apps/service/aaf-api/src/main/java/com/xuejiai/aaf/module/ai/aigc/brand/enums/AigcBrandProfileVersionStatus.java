package com.xuejiai.aaf.module.ai.aigc.brand.enums;

/** 品牌资料版本状态。 */
public enum AigcBrandProfileVersionStatus {
    DRAFT("draft"),
    PUBLISHED("published");

    private final String code;

    AigcBrandProfileVersionStatus(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
