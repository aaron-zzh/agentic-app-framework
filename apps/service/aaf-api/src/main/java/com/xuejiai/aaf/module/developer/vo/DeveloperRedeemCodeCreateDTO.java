package com.xuejiai.aaf.module.developer.vo;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;

/**
 * 创建兑换码请求。
 *
 * <p>type=TOKEN 时 tokenAmount 必填；type=LICENSE 时 planCode 必填。
 */
public record DeveloperRedeemCodeCreateDTO(
        /** 兑换码类型：TOKEN / LICENSE */
        @NotBlank String type,
        /** type=TOKEN 时发放的 token 额度 */
        Long tokenAmount,
        /** type=LICENSE 时绑定的套餐 code */
        String planCode,
        LocalDateTime expiresAt,
        String remark) {}
