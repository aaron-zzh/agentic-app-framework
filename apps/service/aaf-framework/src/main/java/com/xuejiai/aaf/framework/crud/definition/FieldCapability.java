package com.xuejiai.aaf.framework.crud.definition;

/** 字段可参与的服务端能力。 */
public enum FieldCapability {
    READ,
    WRITE,
    FILTER,
    SORT,
    AGGREGATE,
    EXPORT,
    REFERENCE
}
