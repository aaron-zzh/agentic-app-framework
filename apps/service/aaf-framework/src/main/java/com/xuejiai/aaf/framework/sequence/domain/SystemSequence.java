package com.xuejiai.aaf.framework.sequence.domain;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 序列号配置。 */
@Getter
@Setter
@Entity
@Table(name = "sys_sequence")
@SQLDelete(
        sql =
                "UPDATE sys_sequence SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class SystemSequence extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    /** 业务唯一编码，调用方通过此 code 获取序列号 */
    @Column(nullable = false, unique = true, length = 50)
    private String code;

    /** 前缀模板，支持 %(year)s %(month)s %(day)s %(y)s %(doy)s */
    @Column(length = 100)
    private String prefix;

    /** 后缀模板 */
    @Column(length = 100)
    private String suffix;

    /** 下一个值（记录用，实际由 PG SEQUENCE 驱动） */
    @Column(nullable = false)
    private Long numberNext = 1L;

    /** 步长 */
    @Column(nullable = false)
    private Integer numberIncrement = 1;

    /** 数字补零位数 */
    @Column(nullable = false)
    private Integer padding = 4;

    /** 是否按日期分段（每月独立计数） */
    @Column(nullable = false)
    private Boolean useDateRange = false;

    /** 是否启用 */
    @Column(nullable = false)
    private Boolean active = true;
}
