package com.xuejiai.aaf.module.system.dict.vo;

import java.time.LocalDateTime;

/** 字典类型响应 VO。 */
public record DictTypeVO(
        Long id,
        String name,
        String type,
        Integer status,
        String remark,
        LocalDateTime createTime) {}
