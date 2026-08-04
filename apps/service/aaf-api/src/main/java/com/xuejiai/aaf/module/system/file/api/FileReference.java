package com.xuejiai.aaf.module.system.file.api;

import java.util.Objects;

/** 业务对象对物理文件的稳定引用。 */
public record FileReference(String refType, Long refId, String refField, String purpose) {

    public FileReference {
        Objects.requireNonNull(refType, "refType 不能为空");
        Objects.requireNonNull(refId, "refId 不能为空");
        Objects.requireNonNull(refField, "refField 不能为空");
    }
}
