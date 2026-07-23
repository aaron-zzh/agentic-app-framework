package com.xuejiai.aaf.module.system.menu.repository;

import java.util.List;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.system.menu.domain.SysMenu;

/** 菜单仓储 */
public interface SysMenuRepository extends CrudEntityRepository<SysMenu> {

    List<SysMenu> findByVisibleTrueOrderBySortOrder();

    List<SysMenu> findByParentIdOrderBySortOrder(Long parentId);

    List<SysMenu> findAllByOrderBySortOrder();
}
