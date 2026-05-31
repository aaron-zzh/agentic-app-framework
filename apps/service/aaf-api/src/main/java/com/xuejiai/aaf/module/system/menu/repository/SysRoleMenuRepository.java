package com.xuejiai.aaf.module.system.menu.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.system.menu.domain.SysRoleMenu;

/**
 * 角色-菜单关联仓储
 */
public interface SysRoleMenuRepository extends JpaRepository<SysRoleMenu, SysRoleMenu.Id> {

    List<SysRoleMenu> findByRoleId(Long roleId);

    List<SysRoleMenu> findByRoleIdIn(List<Long> roleIds);

    void deleteByRoleId(Long roleId);
}
