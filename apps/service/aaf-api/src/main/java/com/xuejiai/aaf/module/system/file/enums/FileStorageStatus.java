package com.xuejiai.aaf.module.system.file.enums;

/** 文件物理对象生命周期。 */
public enum FileStorageStatus {
    ACTIVE,
    PENDING_DELETE,
    DELETE_FAILED,
    DELETED,
    ORPHAN
}
