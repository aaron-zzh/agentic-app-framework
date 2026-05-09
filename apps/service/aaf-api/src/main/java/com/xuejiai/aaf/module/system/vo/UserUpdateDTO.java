package com.xuejiai.aaf.module.system.vo;

import jakarta.validation.constraints.Size;

/** 更新用户请求。 */
public record UserUpdateDTO(
        @Size(max = 100, message = "昵称最长 100 字符") String nickname, Integer status) {}
