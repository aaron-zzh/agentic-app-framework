package com.xuejiai.aaf.module.billing.vo;

import java.time.LocalDateTime;

public record LevelVO(
        Long id,
        String code,
        String name,
        Integer expMin,
        Integer expMax,
        String perks,
        Integer sort,
        LocalDateTime createTime,
        LocalDateTime updateTime) {}
