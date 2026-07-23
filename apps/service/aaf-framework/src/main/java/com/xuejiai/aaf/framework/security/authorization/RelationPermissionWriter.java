package com.xuejiai.aaf.framework.security.authorization;

/** ReBAC 关系写入扩展点；业务模块仅依赖该接口，不直接依赖关系存储实现。 */
public interface RelationPermissionWriter {

    /** 为主体授予对象关系。 */
    void grant(Long subjectId, String objectType, String objectId, String relation);
}
