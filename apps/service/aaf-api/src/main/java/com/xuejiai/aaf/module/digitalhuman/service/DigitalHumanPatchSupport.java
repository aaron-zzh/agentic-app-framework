package com.xuejiai.aaf.module.digitalhuman.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.digitalhuman.ErrorCodeConstants.DIGITAL_HUMAN_FIELD_NOT_NULLABLE;
import static com.xuejiai.aaf.module.digitalhuman.ErrorCodeConstants.DIGITAL_HUMAN_VERSION_CONFLICT;

import java.util.function.Consumer;

import com.xuejiai.aaf.framework.crud.definition.Patch;

/** 数字人 Patch 更新支持。 */
final class DigitalHumanPatchSupport {

    private DigitalHumanPatchSupport() {}

    /** 校验乐观锁版本，返回更新后的版本号。 */
    static int requireVersion(Integer currentVersion, Integer expectedVersion) {
        var current = currentVersion == null ? 0 : currentVersion;
        if (expectedVersion == null || expectedVersion != current) {
            throw exception(DIGITAL_HUMAN_VERSION_CONFLICT);
        }
        return current + 1;
    }

    /** 非空字段：未传不改，显式 null 拒绝。 */
    static <T> void required(Patch<T> patch, String field, Consumer<T> setter) {
        if (patch.isAbsent()) return;
        if (patch.isNullValue()) throw exception(DIGITAL_HUMAN_FIELD_NOT_NULLABLE, field);
        setter.accept(patch.valueOrNull());
    }

    /** 可空字段：未传不改，显式 null 清空。 */
    static <T> void nullable(Patch<T> patch, Consumer<T> setter) {
        if (!patch.isAbsent()) setter.accept(patch.valueOrNull());
    }
}
