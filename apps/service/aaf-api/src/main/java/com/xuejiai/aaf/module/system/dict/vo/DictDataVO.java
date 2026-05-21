package com.xuejiai.aaf.module.system.dict.vo;

import java.time.LocalDateTime;

/** 字典数据响应 VO。 */
public record DictDataVO(
        Long id,
        String dictType,
        String label,
        String value,
        Integer sort,
        Integer status,
        String colorType,
        String cssClass,
        String remark,
        LocalDateTime createTime) {}
