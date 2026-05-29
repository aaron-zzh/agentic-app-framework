package com.xuejiai.aaf.module.system.menu.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.system.menu.domain.SysMenu;
import com.xuejiai.aaf.module.system.menu.repository.SysMenuRepository;
import com.xuejiai.aaf.module.system.menu.vo.MenuCreateDTO;
import com.xuejiai.aaf.module.system.menu.vo.MenuVO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 菜单服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MenuService {

    private final SysMenuRepository menuRepository;

    /** 获取当前用户菜单树（当前简化：返回所有 visible=true 的菜单） */
    public List<MenuVO> getUserMenuTree(Long userId) {
        var menus = menuRepository.findByVisibleTrueOrderBySortOrder();
        return buildTree(menus);
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
