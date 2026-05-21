package com.xuejiai.aaf.module.system.user.vo;

import java.time.LocalDateTime;

/** 用户响应。 */
public record UserVO(
        Long id,
        String username,
        String nickname,
        Integer status,
        LocalDateTime createTime,
        LocalDateTime updateTime) {}
