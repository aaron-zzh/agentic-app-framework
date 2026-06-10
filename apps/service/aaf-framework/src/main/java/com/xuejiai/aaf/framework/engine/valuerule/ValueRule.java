package com.xuejiai.aaf.framework.engine.valuerule;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 价值观/伦理约束规则实体。 */
@Getter
@Setter
@Entity
@Table(name = "ai_value_rule")
public class ValueRule extends BaseEntity {

    @Column(nullable = false, length = 128)
    private String name;

    /** 规则类型：FORBIDDEN / REQUIRED / PREFERRED */
    @Column(nullable = false, length = 32)
    private String ruleType = "FORBIDDEN";

    /** 规则条件描述（关键词或 LLM 判断提示词） */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String condition;

    /** 优先级，越高越先判断 */
    @Column(nullable = false)
    private Integer priority = 0;

    @Column(nullable = false)
    private Boolean enabled = true;

    /** 作用范围：GLOBAL / TENANT */
    @Column(nullable = false, length = 16)
    private String scope = "GLOBAL";

    private Long tenantId;
}
