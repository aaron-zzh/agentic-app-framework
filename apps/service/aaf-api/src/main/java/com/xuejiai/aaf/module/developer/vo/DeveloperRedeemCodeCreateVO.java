package com.xuejiai.aaf.module.developer.vo;

/** 创建兑换码响应。 */
public record DeveloperRedeemCodeCreateVO(
        Long id,
        String code,
        String codePrefix,
        /** type=TOKEN 时有效 */
        Long tokenAmount,
        /** type=LICENSE 时返回预签发的 license.jwt */
        String licenseJwt) {}
