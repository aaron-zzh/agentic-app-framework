package com.xuejiai.aaf.framework.crud.enforcement;

import java.util.Objects;

import org.springframework.data.jpa.domain.Specification;

/** 当前主体对单一资源的已编译记录规则。 */
public record RecordRule<E>(Specification<E> specification, String accessVersion) {

    public RecordRule {
        specification = Objects.requireNonNull(specification, "specification");
        accessVersion = Objects.requireNonNull(accessVersion, "accessVersion");
    }

    public static <E> RecordRule<E> allowAll(String accessVersion) {
        return new RecordRule<>((root, query, cb) -> null, accessVersion);
    }

    public static <E> RecordRule<E> denyAll(String accessVersion) {
        return new RecordRule<>((root, query, cb) -> cb.disjunction(), accessVersion);
    }
}
