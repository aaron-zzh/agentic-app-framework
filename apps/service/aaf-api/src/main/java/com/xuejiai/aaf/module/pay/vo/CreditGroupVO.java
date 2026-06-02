package com.xuejiai.aaf.module.pay.vo;

import java.util.List;

/** 积分分组明细（按 batch_type 汇总，供 Header 用户弹窗展示） */
public record CreditGroupVO(String batchType, String label, long remain, List<Item> items) {

    public record Item(String label, long remain) {}
}
