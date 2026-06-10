package com.xuejiai.aaf.module.chat.message.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 更新消息请求（仅允许修改内容）。
 *
 * @author AaronZZH & Kiro
 */
@Schema(description = "更新消息请求")
public record MessageUpdateDTO(@Schema(description = "消息内容") String content) {}
