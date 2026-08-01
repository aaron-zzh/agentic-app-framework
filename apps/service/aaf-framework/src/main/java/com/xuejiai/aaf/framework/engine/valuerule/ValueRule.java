package com.xuejiai.aaf.framework.engine.valuerule;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 价值观/伦理约束规则实体。
 *
 * <p>标注 {@link com.xuejiai.aaf.framework.org.OrgIgnore}：{@code scope} 字段声明了 GLOBAL/ORG 两种值域，
 * 但当前代码库没有任何写入路径把 {@code scope} 设为 ORG 或写入 {@code org_id}——运行时该表实际上就是全局规则表。
 * 若未来落地按组织隔离价值观规则，需先补齐 org_id 写入路径，再移除本标注（否则组织过滤会让 ORG 规则查询静默返回空）。
 */
@Getter
@Setter
@Entity
@Table(name = "ai_value_rule")
@com.xuejiai.aaf.framework.org.OrgIgnore
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

    /** 作用范围：GLOBAL / ORG */
    @Column(nullable = false, length = 16)
    private String scope = "GLOBAL";
}
