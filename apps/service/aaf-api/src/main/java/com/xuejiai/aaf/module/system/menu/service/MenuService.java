package com.xuejiai.aaf.module.system.menu.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.system.menu.domain.SysMenu;
import com.xuejiai.aaf.module.system.menu.repository.SysMenuRepository;
import com.xuejiai.aaf.module.system.menu.repository.SysRoleMenuRepository;
import com.xuejiai.aaf.module.system.menu.vo.MenuCreateDTO;
import com.xuejiai.aaf.module.system.menu.vo.MenuVO;
import com.xuejiai.aaf.module.system.role.domain.UserRole;
import com.xuejiai.aaf.module.system.role.repository.RoleRepository;
import com.xuejiai.aaf.module.system.role.repository.UserRoleRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 菜单服务——接入四层权限模型 Layer 1（RBAC 角色→菜单过滤）。
 *
 * <p>权限判定流程：
 * <ol>
 *   <li>查用户角色（sys_user_role）</li>
 *   <li>超级管理员（code=super_admin）→ 返回全部菜单</li>
 *   <li>普通角色 → 查角色关联菜单（sys_role_menu）→ 过滤后构建树</li>
 * </ol>
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MenuService {

    private static final String SUPER_ADMIN_CODE = "super_admin";

    private final SysMenuRepository menuRepository;
    private final SysRoleMenuRepository roleMenuRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;

    /**
     * 获取当前用户菜单树（RBAC 角色过滤）。
     *
     * <p>超级管理员返回全部可见菜单；普通用户只返回角色关联的菜单。
     */
    public List<MenuVO> getUserMenuTree(Long userId) {
        // 查用户角色
        var userRoles = userRoleRepository.findByUserIdAndDeletedFalse(userId);
        if (userRoles.isEmpty()) {
            // 无角色用户返回空菜单
            return List.of();
        }

        var roleIds = userRoles.stream().map(UserRole::getRoleId).toList();

        // 判断是否超级管理员
        var roles = roleRepository.findAllById(roleIds);
        boolean isSuperAdmin = roles.stream().anyMatch(r -> SUPER_ADMIN_CODE.equals(r.getCode()));

        if (isSuperAdmin) {
            // 超级管理员返回全部可见菜单
            return buildTree(menuRepository.findByVisibleTrueOrderBySortOrder());
        }

        // 普通角色：查角色关联的菜单 ID
        var roleMenus = roleMenuRepository.findByRoleIdIn(roleIds);
        if (roleMenus.isEmpty()) {
            return List.of();
        }
        Set<Long> allowedMenuIds = roleMenus.stream()
                .map(rm -> rm.getMenuId())
                .collect(Collectors.toSet());

        // 过滤：只保留角色关联的可见菜单 + 其父级菜单（确保树结构完整）
        var allMenus = menuRepository.findByVisibleTrueOrderBySortOrder();
        var filteredMenus = allMenus.stream()
                .filter(m -> allowedMenuIds.contains(m.getId()) || isParentOfAllowed(m, allMenus, allowedMenuIds))
                .toList();

        return buildTree(filteredMenus);
    }

    /** 获取全部菜单树（管理用） */
    public List<MenuVO> getFullTree() {
        var menus = menuRepository.findAllByOrderBySortOrder();
        return buildTree(menus);
    }

    @Transactional
    public MenuVO create(MenuCreateDTO dto) {
        var menu = new SysMenu();
        menu.setTitle(dto.title());
        menu.setParentId(dto.parentId());
        menu.setPath(dto.path());
        menu.setIcon(dto.icon());
        menu.setSortOrder(dto.sortOrder() != null ? dto.sortOrder() : 0);
        menu.setVisible(dto.visible() != null ? dto.visible() : true);
        menu.setMenuType(dto.menuType() != null ? dto.menuType() : "MENU");
        menu.setPermission(dto.permission());
        return toVO(menuRepository.save(menu));
    }

    @Transactional
    public MenuVO update(Long id, MenuCreateDTO dto) {
        var menu = requireMenu(id);
        menu.setTitle(dto.title());
        menu.setParentId(dto.parentId());
        menu.setPath(dto.path());
        menu.setIcon(dto.icon());
        if (dto.sortOrder() != null) menu.setSortOrder(dto.sortOrder());
        if (dto.visible() != null) menu.setVisible(dto.visible());
        if (dto.menuType() != null) menu.setMenuType(dto.menuType());
        menu.setPermission(dto.permission());
        return toVO(menuRepository.save(menu));
    }

    @Transactional
    public void delete(Long id) {
        requireMenu(id);
        menuRepository.deleteById(id);
    }

    @Transactional
    public void updateSort(Long id, Integer sortOrder) {
        var menu = requireMenu(id);
        menu.setSortOrder(sortOrder);
        menuRepository.save(menu);
    }

    private SysMenu requireMenu(Long id) {
        return menuRepository
                .findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "菜单不存在"));
    }

    /** 构建树形结构 */
    private List<MenuVO> buildTree(List<SysMenu> menus) {
        Map<Long, List<SysMenu>> childrenMap =
                menus.stream()
                        .filter(m -> m.getParentId() != null)
                        .collect(Collectors.groupingBy(SysMenu::getParentId));

        return menus.stream()
                .filter(m -> m.getParentId() == null)
                .map(m -> toTreeVO(m, childrenMap))
                .toList();
    }

    private MenuVO toTreeVO(SysMenu menu, Map<Long, List<SysMenu>> childrenMap) {
        var children =
                childrenMap.getOrDefault(menu.getId(), List.of()).stream()
                        .map(c -> toTreeVO(c, childrenMap))
                        .toList();
        return new MenuVO(
                menu.getId(),
                menu.getParentId(),
                menu.getTitle(),
                menu.getPath(),
                menu.getIcon(),
                menu.getSortOrder(),
                menu.getVisible(),
                menu.getMenuType(),
                menu.getPermission(),
                children.isEmpty() ? null : children);
    }

    /** 判断菜单是否为被允许菜单的父级（确保树结构完整） */
    private boolean isParentOfAllowed(SysMenu menu, List<SysMenu> allMenus, Set<Long> allowedIds) {
        return allMenus.stream()
                .filter(m -> allowedIds.contains(m.getId()))
                .anyMatch(m -> isAncestor(menu.getId(), m, allMenus));
    }

    /** 递归判断 ancestorId 是否为 menu 的祖先 */
    private boolean isAncestor(Long ancestorId, SysMenu menu, List<SysMenu> allMenus) {
        if (menu.getParentId() == null) return false;
        if (menu.getParentId().equals(ancestorId)) return true;
        return allMenus.stream()
                .filter(m -> m.getId().equals(menu.getParentId()))
                .findFirst()
                .map(parent -> isAncestor(ancestorId, parent, allMenus))
                .orElse(false);
    }

    private MenuVO toVO(SysMenu menu) {
        return new MenuVO(
                menu.getId(),
                menu.getParentId(),
                menu.getTitle(),
                menu.getPath(),
                menu.getIcon(),
                menu.getSortOrder(),
                menu.getVisible(),
                menu.getMenuType(),
                menu.getPermission(),
                null);
    }
}
