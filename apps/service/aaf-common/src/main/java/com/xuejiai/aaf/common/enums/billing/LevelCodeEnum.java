package com.xuejiai.aaf.common.enums.billing;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 会员等级编码 */
@Getter
@AllArgsConstructor
public enum LevelCodeEnum {
    L0("L0", "普通会员"),
    L1("L1", "银牌会员"),
    L2("L2", "金牌会员");

    private final String code;
    private final String label;
}
