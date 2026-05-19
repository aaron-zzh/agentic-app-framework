package com.xuejiai.aaf.module.system.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.module.system.domain.Role;
import com.xuejiai.aaf.module.system.repository.RoleRepository;
import com.xuejiai.aaf.module.system.vo.RoleCreateDTO;
import com.xuejiai.aaf.module.system.vo.RoleUpdateDTO;
import com.xuejiai.aaf.module.system.vo.RoleVO;

import lombok.RequiredArgsConstructor;

/** 角色管理服务。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoleService {

    private final RoleRepository roleRepository;

    public List<RoleVO> list() {
        return roleRepository.findAll().stream().map(this::toVO).toList();
    }

    public RoleVO getById(Long id) {
        return toVO(
                roleRepository
                        .findById(id)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "角色不存在")));
    }

    @Transactional
    public RoleVO create(RoleCreateDTO dto) {
        if (roleRepository.existsByCodeAndDeletedFalse(dto.code())) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "角色编码已存在");
        }
        var role = new Role();
        role.setCode(dto.code());
        role.setName(dto.name());
        role.setDescription(dto.description());
        return toVO(roleRepository.save(role));
    }

    @Transactional
    public RoleVO update(Long id, RoleUpdateDTO dto) {
        var role =
                roleRepository
                        .findById(id)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "角色不存在"));
        if (dto.name() != null) {
            role.setName(dto.name());
        }
        if (dto.description() != null) {
            role.setDescription(dto.description());
        }
        if (dto.status() != null) {
            role.setStatus(dto.status());
        }
        return toVO(roleRepository.save(role));
    }

    @Transactional
    public void delete(Long id) {
        roleRepository.deleteById(id);
    }

    private RoleVO toVO(Role role) {
        return new RoleVO(
                role.getId(),
                role.getCode(),
                role.getName(),
                role.getDescription(),
                role.getStatus(),
                role.getCreateTime());
    }
}
