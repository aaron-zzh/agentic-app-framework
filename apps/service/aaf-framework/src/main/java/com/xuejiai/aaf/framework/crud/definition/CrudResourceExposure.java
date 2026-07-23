package com.xuejiai.aaf.framework.crud.definition;

/** Catalog 资源允许被哪些受信任消费者发现。 */
public enum CrudResourceExposure {
    HTTP,
    ENTITY_DEF,
    REFERENCE,
    AI_ACTION
}
