package com.xuejiai.aaf.module.ai.aigc.configuration.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.ai.aigc.configuration.AigcConfigurationErrorCode.VERSION_CONFLICT;

import java.util.function.Consumer;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.framework.crud.definition.Patch;

/** AIGC 配置 Patch 更新支持。 */
final class AigcConfigurationPatchSupport {

    private AigcConfigurationPatchSupport() {}

    static int requireVersion(Integer currentVersion, Integer expectedVersion) {
        var current = currentVersion == null ? 0 : currentVersion;
        if (expectedVersion == null || expectedVersion != current) {
            throw exception(VERSION_CONFLICT);
        }
        return current + 1;
    }

    static <T> void required(Patch<T> patch, String field, Consumer<T> setter) {
        if (patch.isAbsent()) return;
        if (patch.isNullValue()) throw new BusinessException(400, field + " 不能为 null");
        setter.accept(patch.valueOrNull());
    }

    static <T> void nullable(Patch<T> patch, Consumer<T> setter) {
        if (!patch.isAbsent()) setter.accept(patch.valueOrNull());
    }
}
