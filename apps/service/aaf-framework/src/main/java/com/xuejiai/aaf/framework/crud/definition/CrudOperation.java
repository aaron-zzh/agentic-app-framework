package com.xuejiai.aaf.framework.crud.definition;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/** BaseCrud 的受保护技术入口与 capability 唯一词汇源。 */
public enum CrudOperation {
    PAGE("page", CrudAction.READ),
    QUERY("queryWindow", CrudAction.READ),
    GET("get", CrudAction.READ),
    BATCH_READ("batchRead", CrudAction.READ),
    OPTIONS("options", CrudAction.READ),
    META("meta", CrudAction.READ),
    EXPORT("export", CrudAction.EXPORT),
    GROUP("group", CrudAction.AGGREGATE),
    IMPORT("import", CrudAction.IMPORT),
    VALIDATE("validate", CrudAction.CREATE),
    CREATE("create", CrudAction.CREATE),
    UPDATE("update", CrudAction.UPDATE),
    DELETE("delete", CrudAction.DELETE),
    DELETE_BATCH("deleteBatch", CrudAction.DELETE),
    ARCHIVE("archive", CrudAction.DELETE),
    RESTORE("restore", CrudAction.UPDATE),
    REFERENCE("reference", CrudAction.REFERENCE);

    private static final Map<String, CrudOperation> BY_CAPABILITY =
            Arrays.stream(values())
                    .collect(
                            Collectors.toUnmodifiableMap(
                                    CrudOperation::capability, operation -> operation));

    private final String capability;
    private final CrudAction action;

    CrudOperation(String capability, CrudAction action) {
        this.capability = capability;
        this.action = action;
    }

    public static CrudOperation fromCapability(String capability) {
        var operation = capability == null ? null : BY_CAPABILITY.get(capability);
        if (operation == null) {
            throw new IllegalArgumentException("未知 CRUD capability: " + capability);
        }
        return operation;
    }

    public String capability() {
        return capability;
    }

    public CrudAction action() {
        return action;
    }

    /** 是否允许在 super_admin 全组织上下文中执行。 */
    public boolean allowedInAllOrganizations() {
        return switch (this) {
            case PAGE, QUERY, GET, OPTIONS, META -> true;
            case BATCH_READ,
                    EXPORT,
                    GROUP,
                    IMPORT,
                    VALIDATE,
                    CREATE,
                    UPDATE,
                    DELETE,
                    DELETE_BATCH,
                    ARCHIVE,
                    RESTORE,
                    REFERENCE ->
                    false;
        };
    }
}
