package com.xuejiai.aaf.module.system.vo;

/**
 * 意图识别结果。
 *
 * @author AaronZZH & Kiro
 */
public record IntentResult(
        String intent,
        double confidence,
        String action
) {}
