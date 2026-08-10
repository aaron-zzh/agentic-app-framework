package com.xuejiai.aaf.framework.crud.filter;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.jpa.domain.Specification;

import com.xuejiai.aaf.common.enums.ArrayValuable;
import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.common.validation.InEnum;
import com.xuejiai.aaf.framework.crud.definition.CrudResourceTypeContract;
import com.xuejiai.aaf.framework.crud.definition.CrudViewDefinition;

import jakarta.persistence.Column;

/** 资源声明的高级筛选字段白名单。 */
public final class CrudFilterSchema<E> {

    public enum Mode {
        AUTO,
        NONE,
        EXPLICIT
    }

    private final Mode mode;
    private final List<CrudFilterField<E>> fields;
    private final List<CrudFilterFieldMeta> metas;
    private final Map<String, CrudFilterRule<E>> rules;

    private CrudFilterSchema(Mode mode, List<CrudFilterField<E>> fields) {
        this.mode = mode;
        this.fields = List.copyOf(fields);

        var mutableRules = new LinkedHashMap<String, CrudFilterRule<E>>();
        var mutableMetas = new ArrayList<CrudFilterFieldMeta>(fields.size());
        for (var field : this.fields) {
            if (mutableRules.putIfAbsent(field.name(), field.rule()) != null) {
                throw new IllegalArgumentException("筛选字段重复: " + field.name());
            }
            mutableMetas.add(field.toMeta());
        }
        this.rules = Map.copyOf(mutableRules);
        this.metas = List.copyOf(mutableMetas);
    }

    @SafeVarargs
    public static <E> CrudFilterSchema<E> of(CrudFilterField<E>... fields) {
        return new CrudFilterSchema<>(Mode.EXPLICIT, List.of(fields));
    }

    /** 由资源类型合同与公开列表字段中央生成安全默认筛选。 */
    public static <E> CrudFilterSchema<E> auto() {
        return new CrudFilterSchema<>(Mode.AUTO, List.of());
    }

    /** 明确禁止资源使用通用筛选。 */
    public static <E> CrudFilterSchema<E> none() {
        return new CrudFilterSchema<>(Mode.NONE, List.of());
    }

    /** 兼容原有明确空筛选语义；新代码使用 {@link #none()}。 */
    public static <E> CrudFilterSchema<E> empty() {
        return none();
    }

    /** 将 AUTO 模式解析为不可变的显式安全筛选 schema。 */
    public static <E extends BaseEntity> CrudFilterSchema<E> resolve(
            CrudFilterSchema<E> schema,
            CrudResourceTypeContract<E> types,
            CrudViewDefinition view) {
        return schema.mode == Mode.AUTO ? safeDefaults(types, view) : schema;
    }

    public Mode mode() {
        return mode;
    }

    /**
     * 从类型化查询 DTO 与公开列表字段推导保守默认筛选能力。
     *
     * <p>候选字段必须同时存在于 PageDTO 自身声明、list 字段集和实体属性中；只推导已有安全规则的字符串、固定枚举和 LocalDateTime， 不从
     * EntityDef、客户端请求或关联展示对象推导能力。
     */
    public static <E extends BaseEntity> CrudFilterSchema<E> safeDefaults(
            CrudResourceTypeContract<E> types, CrudViewDefinition view) {
        var listFields = view.fieldSets().getOrDefault("list", Set.of());
        var inferred = new ArrayList<CrudFilterField<E>>();
        Arrays.stream(types.pageType().getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .filter(field -> !field.isSynthetic())
                .filter(field -> listFields.contains(field.getName()))
                .forEach(
                        pageField -> {
                            var entityField = findField(types.entityType(), pageField.getName());
                            if (entityField == null) {
                                return;
                            }
                            var filterField =
                                    CrudFilterSchema.<E>inferField(pageField, entityField);
                            if (filterField != null) {
                                inferred.add(filterField);
                            }
                        });
        return new CrudFilterSchema<>(Mode.EXPLICIT, inferred);
    }

    private static <E> CrudFilterField<E> inferField(Field pageField, Field entityField) {
        var name = pageField.getName();
        if (String.class.equals(pageField.getType())
                && String.class.equals(entityField.getType())) {
            var inEnum = pageField.getAnnotation(InEnum.class);
            if (inEnum == null) {
                return CrudFilterField.text(name);
            }
            var allowedValues = enumValues(inEnum);
            return isNullable(entityField)
                    ? CrudFilterField.nullableEnumValues(name, allowedValues)
                    : CrudFilterField.enumValues(name, allowedValues);
        }
        if (LocalDateTime.class.equals(pageField.getType())
                && LocalDateTime.class.equals(entityField.getType())) {
            return CrudFilterField.localDateTime(name);
        }
        return null;
    }

    private static Set<String> enumValues(InEnum annotation) {
        ArrayValuable<?>[] constants = annotation.value().getEnumConstants();
        if (constants.length == 0) {
            return Set.of();
        }
        var values = new LinkedHashSet<String>();
        Arrays.stream(constants[0].array()).map(String::valueOf).forEach(values::add);
        return Set.copyOf(values);
    }

    private static boolean isNullable(Field field) {
        var column = field.getAnnotation(Column.class);
        return column == null || column.nullable();
    }

    private static Field findField(Class<?> type, String name) {
        for (var current = type;
                current != null && !Object.class.equals(current);
                current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // 继续检查父类字段。
            }
        }
        return null;
    }

    /** 按声明顺序返回供客户端渲染的筛选字段元数据。 */
    public List<CrudFilterFieldMeta> metas() {
        return metas;
    }

    /** 使用同一份白名单和规则构建服务端查询条件。 */
    public Specification<E> build(List<CrudFilter> filters, FilterEvaluationContext context) {
        return CrudFilterSupport.build(filters, rules, context);
    }
}
