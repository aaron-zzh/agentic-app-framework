package com.xuejiai.aaf.module.system.vo;

import java.time.LocalDateTime;

/** 用户响应。 */
public record UserRespVO(
        Long id,
        String username,
        String nickname,
        Short status,
        LocalDateTime createTime,
        LocalDateTime updateTime) {}
