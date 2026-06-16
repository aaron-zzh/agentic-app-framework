package com.xuejiai.aaf.module.system.profile.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 用户画像维度值。 */
@Getter
@Setter
@Entity
@Table(name = "sys_profile_dimension_value")
public class ProfileDimensionValue extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "dimension_id", nullable = false)
    private Long dimensionId;

    @Column(name = "value_text")
    private String valueText;

    @Column(name = "value_number", precision = 18, scale = 4)
    private BigDecimal valueNumber;

    @Column(name = "value_tags", columnDefinition = "jsonb")
    private String valueTags;

    @Column(name = "confidence", precision = 3, scale = 2)
    private BigDecimal confidence = BigDecimal.ONE;

    @Column(name = "source", nullable = false, length = 32)
    private String source;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
}
