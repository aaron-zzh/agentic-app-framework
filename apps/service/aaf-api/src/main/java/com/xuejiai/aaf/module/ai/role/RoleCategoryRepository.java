package com.xuejiai.aaf.module.ai.role;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;

/**
 * 角色分类仓储。
 *
 * <p>继承的无条件读取只供 CRUD 管线与角色引用校验使用；用户面读取一律经 {@link RoleCategoryService} 的安全管线。
 */
public interface RoleCategoryRepository extends CrudEntityRepository<AiRoleCategory> {

    /** 稳定业务码是否已被占用。 */
    boolean existsByCode(String code);

    /** 系统分类名称是否已被占用。 */
    boolean existsByVisibilityAndName(String visibility, String name);

    /** 系统分类名称是否已被其他分类占用。 */
    boolean existsByVisibilityAndNameAndIdNot(String visibility, String name, Long id);

    /** 所有者的私有分类名称是否已被占用。 */
    boolean existsByVisibilityAndOwnerIdAndName(String visibility, Long ownerId, String name);

    /** 所有者的私有分类名称是否已被其他分类占用。 */
    boolean existsByVisibilityAndOwnerIdAndNameAndIdNot(
            String visibility, Long ownerId, String name, Long id);
}
