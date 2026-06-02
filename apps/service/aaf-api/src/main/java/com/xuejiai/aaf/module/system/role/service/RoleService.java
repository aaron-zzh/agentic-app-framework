package com.xuejiai.aaf.module.system.role.service;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.system.role.domain.Role;
import com.xuejiai.aaf.module.system.role.repository.RoleRepository;
import com.xuejiai.aaf.module.system.role.vo.RoleCreateDTO;
import com.xuejiai.aaf.module.system.role.vo.RolePageParam;
import com.xuejiai.aaf.module.system.role.vo.RoleUpdateDTO;
import com.xuejiai.aaf.module.system.role.vo.RoleVO;

import lombok.RequiredArgsConstructor;

/**
 * 角色管理服务。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoleService
        extends BaseCrudService<Role, RoleVO, RoleCreateDTO, RoleUpdateDTO, RolePageParam> {

    private final RoleRepository roleRepository;

    @Override
    protected JpaRepository<Role, Long> getRepository() {
        return roleRepository;
    }

    @Override
    protected JpaSpecificationExecutor<Role> getSpecExecutor() {
        return roleRepository;
    }

    @Override
    protected RoleVO toVO(Role role) {
        return new RoleVO(
                role.getId(),
                role.getCode(),
                role.getName(),
                role.getDescription(),
                role.getStatus(),
                role.getCreateTime());
    }

    @Override
    protected Role toEntity(RoleCreateDTO dto) {
        if (roleRepository.existsByCodeAndDeletedFalse(dto.code())) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "角色编码已存在");
        }
        var role = new Role();
        role.setCode(dto.code());
        role.setName(dto.name());
        role.setDescription(dto.description());
        return role;
    }

    @Override
    protected void updateEntity(Role role, RoleUpdateDTO dto) {
        if (dto.name() != null) {
            role.setName(dto.name());
        }
        if (dto.description() != null) {
            role.setDescription(dto.description());
        }
        if (dto.status() != null) {
            role.setStatus(dto.status());
        }
    }

    @Override
    protected Specification<Role> buildSpec(RolePageParam request) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (request.getKeyword() != null && !request.getKeyword().isBlank()) {
                var keyword = "%" + request.getKeyword().trim() + "%";
                predicates.add(
                        cb.or(
                                cb.like(root.get("code"), keyword),
                                cb.like(root.get("name"), keyword)));
            }
            if (request.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), request.getStatus()));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    @Override
    protected Specification<Role> buildOptionSpec(String keyword) {
        return (root, query, cb) -> {
            if (keyword == null || keyword.isBlank()) {
                return null;
            }
            var pattern = "%" + keyword.trim() + "%";
            return cb.or(cb.like(root.get("code"), pattern), cb.like(root.get("name"), pattern));
        };
    }

    @Override
    protected String entityName() {
        return "角色";
    }

    @Override
    protected String entitySlug() {
        return "system-role";
    }

    @Override
    protected String permissionResource() {
        return "role";
    }
}
