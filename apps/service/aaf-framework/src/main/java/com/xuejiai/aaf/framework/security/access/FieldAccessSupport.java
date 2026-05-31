package com.xuejiai.aaf.framework.security.access;

import java.util.Set;

/** 字段级权限 SPI。 */
public interface FieldAccessSupport {

    Set<String> hiddenFields(String entitySlug, Long userId, String action);
}
