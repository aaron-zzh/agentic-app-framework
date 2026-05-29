package com.xuejiai.aaf.module.pay.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 订单明细行 */
@Getter
@Setter
@Entity
@Table(name = "biz_order_item")
@SQLDelete(
        sql =
                "UPDATE biz_order_item SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ? AND version = ?")
public class BizOrderItem extends BaseEntity {

    /** 关联订单 ID */
    @Column(name = "order_id", nullable = false)
    private Long orderId;

    /** 产品类型：CREDIT_PACK/TOKEN_PACK/SUBSCRIPTION/AGENT/TOOL/KNOWLEDGE */
    @Column(name = "product_type", nullable = false, length = 32)
    private String productType;

    /** 产品 ID */
    @Column(name = "product_id", length = 64)
    private String productId;

    /** 产品名称 */
    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    /** 数量 */
    @Column(name = "quantity", nullable = false)
    private Integer quantity = 1;

    /** 单价（分） */
    @Column(name = "unit_price", nullable = false)
    private Long unitPrice;

    /** 小计（分） */
    @Column(name = "total_price", nullable = false)
    private Long totalPrice;
}
