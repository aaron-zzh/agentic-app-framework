package com.xuejiai.aaf.common.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Predicate;

/**
 * JPA Specification 链式构建器。值为 null/空时自动跳过条件。
 *
 * <p>使用示例：
 * <pre>{@code
 * Specification<User> spec = SpecificationBuilder.<User>builder()
 *     .likeIfPresent("username", req.getUsername())
 *     .eqIfPresent("status", req.getStatus())
 *     .build();
 * }</pre>
 */
public class SpecificationBuilder<T> {

    private final List<SpecCondition> conditions = new ArrayList<>();

    public static <T> SpecificationBuilder<T> builder() {
        return new SpecificationBuilder<>();
    }

    public SpecificationBuilder<T> likeIfPresent(String field, String value) {
        if (value != null && !value.isBlank()) {
            conditions.add((root, cb) -> cb.like(root.get(field), "%" + value + "%"));
        }
        return this;
    }

    public SpecificationBuilder<T> eqIfPresent(String field, Object value) {
        if (value != null) {
            conditions.add((root, cb) -> cb.equal(root.get(field), value));
        }
        return this;
    }

    public SpecificationBuilder<T> neIfPresent(String field, Object value) {
        if (value != null) {
            conditions.add((root, cb) -> cb.notEqual(root.get(field), value));
        }
        return this;
    }

    public SpecificationBuilder<T> gtIfPresent(String field, Comparable<?> value) {
        if (value != null) {
            conditions.add((root, cb) -> cb.greaterThan(root.get(field), (Comparable) value));
        }
        return this;
    }

    public SpecificationBuilder<T> geIfPresent(String field, Comparable<?> value) {
        if (value != null) {
            conditions.add((root, cb) -> cb.greaterThanOrEqualTo(root.get(field), (Comparable) value));
        }
        return this;
    }

    public SpecificationBuilder<T> ltIfPresent(String field, Comparable<?> value) {
        if (value != null) {
            conditions.add((root, cb) -> cb.lessThan(root.get(field), (Comparable) value));
        }
        return this;
    }

    public SpecificationBuilder<T> leIfPresent(String field, Comparable<?> value) {
        if (value != null) {
            conditions.add((root, cb) -> cb.lessThanOrEqualTo(root.get(field), (Comparable) value));
        }
        return this;
    }

    public SpecificationBuilder<T> betweenIfPresent(String field, Comparable<?> val1, Comparable<?> val2) {
        if (val1 != null && val2 != null) {
            conditions.add((root, cb) -> cb.between(root.get(field), (Comparable) val1, (Comparable) val2));
        } else if (val1 != null) {
            geIfPresent(field, val1);
        } else if (val2 != null) {
            leIfPresent(field, val2);
        }
        return this;
    }

    public SpecificationBuilder<T> inIfPresent(String field, Collection<?> values) {
        if (values != null && !values.isEmpty()) {
            conditions.add((root, cb) -> root.get(field).in(values));
        }
        return this;
    }

    public Specification<T> build() {
        return (root, query, cb) -> {
            if (conditions.isEmpty()) {
                return null;
            }
            Predicate[] predicates = conditions.stream()
                    .map(c -> c.toPredicate(root, cb))
                    .toArray(Predicate[]::new);
            return cb.and(predicates);
        };
    }

    @FunctionalInterface
    private interface SpecCondition {
        Predicate toPredicate(jakarta.persistence.criteria.Root<?> root,
                             jakarta.persistence.criteria.CriteriaBuilder cb);
    }
}
