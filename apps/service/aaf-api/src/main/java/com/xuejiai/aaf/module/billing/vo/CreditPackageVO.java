package com.xuejiai.aaf.module.billing.vo;

/** 积分充值套餐视图对象 */
public record CreditPackageVO(
        Long id,
        String name,
        long credits,
        long bonusCredits,
        long price,
        String group,
        boolean recommended) {}
