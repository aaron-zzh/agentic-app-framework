package com.xuejiai.aaf.framework.security.authorization;

import java.util.Map;
import java.util.Set;

import com.xuejiai.aaf.framework.crud.definition.FieldCapability;

/** 字段级权限 SPI；返回值只能从 Catalog 编译上限中删除能力。 */
public interface FieldAccessSupport {

    Map<FieldCapability, Set<String>> deniedFields(String resourceKey, Long subjectId);
}
