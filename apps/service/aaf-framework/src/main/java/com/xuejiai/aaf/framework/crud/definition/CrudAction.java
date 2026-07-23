package com.xuejiai.aaf.framework.crud.definition;

/** CRUD 操作映射到的授权动作。 */
public enum CrudAction {
    READ("read"),
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    EXPORT("export"),
    IMPORT("import"),
    AGGREGATE("aggregate"),
    REFERENCE("reference");

    private final String permissionSegment;

    CrudAction(String permissionSegment) {
        this.permissionSegment = permissionSegment;
    }

    public String permissionSegment() {
        return permissionSegment;
    }
}
