package com.xuejiai.aaf.module.billing.vo;

import jakarta.validation.constraints.Min;

public record LevelUpdateDTO(
        String name, @Min(0) Integer expMin, @Min(0) Integer expMax, String perks, Integer sort) {}
