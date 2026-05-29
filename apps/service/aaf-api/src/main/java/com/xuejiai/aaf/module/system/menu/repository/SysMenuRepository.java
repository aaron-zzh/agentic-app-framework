package com.xuejiai.aaf.module.system.menu.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.system.menu.domain.SysMenu;

/**
 * 菜单仓储
 */
public interface SysMenuRepository extends JpaRepository<SysMenu, Long> {

    List<SysMenu> findByVisibleTrueOrderBySortOrder();

    List<SysMenu> findByParentIdOrderBySortOrder(Long parentId);

    List<SysMenu> findAllByOrderBySortOrder();
}
