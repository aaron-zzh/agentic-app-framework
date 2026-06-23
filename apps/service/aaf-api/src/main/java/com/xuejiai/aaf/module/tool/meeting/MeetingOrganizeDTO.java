package com.xuejiai.aaf.module.tool.meeting;

import jakarta.validation.constraints.NotBlank;

/**
 * 会议记录整理请求参数。
 *
 * @param transcript 原始转写文本（必填）
 * @param modelId 期望模型 ID（可选，走决策链）
 * @param meetingDate 会议日期，格式 yyyy-MM-dd（可选，不传则取当天）
 */
public record MeetingOrganizeDTO(@NotBlank String transcript, String modelId, String meetingDate) {}
