package com.xuejiai.aaf.framework.security.access;

import org.springframework.data.jpa.domain.Specification;

/**
 * 记录规则支持 SPI。
 *
 * <p>BaseCrudService 通过本接口统一注入 L3 行级数据权限；业务模块负责把数据权限规则转换为 JPA Specification。
 */
public interface RecordRuleSupport {

    /**
     * 构建行级数据权限条件。
     *
     * @param entitySlug 实体标识；为空时调用方应跳过
     * @param userId 数据归属用户 ID
     * @return Specification；返回 null 表示无规则、直接跳过
     */
    <T> Specification<T> buildAccessSpec(String entitySlug, Long userId);

    /** 当前用户对实体的记录规则版本。用于查询窗口 token 校验；规则或角色变化后应变化。 */
    default String accessVersion(String entitySlug, Long userId) {
        return "0";
    }
}
