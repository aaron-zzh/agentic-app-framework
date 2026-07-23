package com.xuejiai.aaf.framework.crud.definition;

import java.util.LinkedHashSet;
import java.util.List;

/** 资源可公开的类型化操作上限。 */
public record CrudCapabilityDefinition(List<CrudOperation> operations) {

    private static final List<CrudOperation> READ_OPERATIONS =
            List.of(
                    CrudOperation.PAGE,
                    CrudOperation.QUERY,
                    CrudOperation.GET,
                    CrudOperation.BATCH_READ,
                    CrudOperation.OPTIONS,
                    CrudOperation.META,
                    CrudOperation.EXPORT);

    public CrudCapabilityDefinition {
        if (operations == null) {
            throw new IllegalArgumentException("operations 不能为空");
        }
        var normalized = new LinkedHashSet<CrudOperation>();
        for (var operation : operations) {
            if (operation == null) {
                throw new IllegalArgumentException("operations 不能包含空值");
            }
            normalized.add(operation);
        }
        operations = List.copyOf(normalized);
        if (operations.isEmpty()) {
            throw new IllegalArgumentException("资源操作能力不能为空");
        }
    }

    public static CrudCapabilityDefinition forTypes(CrudResourceTypeContract<?> types) {
        var operations = new LinkedHashSet<>(READ_OPERATIONS);
        var creatable = !Void.class.equals(types.createType());
        var updatable = !Void.class.equals(types.updateType());
        if (creatable) {
            operations.addAll(
                    List.of(CrudOperation.CREATE, CrudOperation.IMPORT, CrudOperation.VALIDATE));
        }
        if (updatable) {
            operations.add(CrudOperation.UPDATE);
            operations.add(CrudOperation.RESTORE);
        }
        if (creatable || updatable) {
            operations.addAll(
                    List.of(
                            CrudOperation.DELETE,
                            CrudOperation.DELETE_BATCH,
                            CrudOperation.ARCHIVE));
        }
        return new CrudCapabilityDefinition(List.copyOf(operations));
    }

    public static CrudCapabilityDefinition optionsOnly() {
        return new CrudCapabilityDefinition(List.of(CrudOperation.OPTIONS));
    }

    /** 返回移除指定操作后的新能力定义。 */
    public CrudCapabilityDefinition without(CrudOperation... disabled) {
        if (disabled == null || disabled.length == 0) {
            return this;
        }
        var remaining = new LinkedHashSet<>(operations);
        for (var operation : disabled) {
            if (operation == null) {
                throw new IllegalArgumentException("disabled 不能包含空值");
            }
            remaining.remove(operation);
        }
        return new CrudCapabilityDefinition(List.copyOf(remaining));
    }
}
