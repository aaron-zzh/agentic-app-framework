package com.xuejiai.aaf.module.content.service;

import java.util.function.Consumer;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.framework.crud.definition.Patch;

/**
 * Content Studio Patch 更新支持。
 *
 * @author AaronZZH & Kiro
 */
final class ContentPatchSupport {

    private ContentPatchSupport() {}

    static int requireVersion(Integer currentVersion, Integer expectedVersion) {
        var current = currentVersion == null ? 0 : currentVersion;
        if (expectedVersion == null || expectedVersion != current) {
            throw new BusinessException(409, "记录已被其他请求修改，请刷新后重试");
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
