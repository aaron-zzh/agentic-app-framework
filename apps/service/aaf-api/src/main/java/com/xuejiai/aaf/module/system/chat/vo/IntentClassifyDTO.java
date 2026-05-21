package com.xuejiai.aaf.module.system.chat.vo;

import jakarta.validation.constraints.NotBlank;

/**
 * 意图识别请求。
 *
 * @author AaronZZH & Kiro
 */
public record IntentClassifyDTO(@NotBlank String text) {}
