package com.xuejiai.aaf.module.ai.role;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.intelligent.assistant.role.AiAssistantRoleRepository;
import com.xuejiai.aaf.framework.intelligent.assistant.role.AiRoleRepository;
import com.xuejiai.aaf.framework.intelligent.assistant.role.Role;
import com.xuejiai.aaf.framework.intelligent.assistant.role.RoleVisibility;
import com.xuejiai.aaf.framework.intelligent.assistant.role.RoleVisibilityLevel;
import com.xuejiai.aaf.framework.security.OperatorContext;

import lombok.RequiredArgsConstructor;

/**
 * 用户私有 Role 服务。
 *
 * <p>所有读写都按当前用户限定为自己的私有角色；他人的私有角色、SYSTEM / PUBLIC 角色一律按"不存在"处理， 不泄露存在性。服务层与仓储层各按 {@code owner_id}
 * 校验一次。私有角色数量不设上限。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyRoleService {

    private static final String NOT_FOUND_MESSAGE = "Role 不存在";

    private final AiRoleRepository roleRepository;
    private final AiAssistantRoleRepository assistantRoleRepository;
    private final RoleReferenceValidator referenceValidator;
    private final OperatorContext operatorContext;

    /** 当前用户的全部私有角色。 */
    public List<RoleVO> list() {
        return roleRepository.findPrivateByOwner(currentUserId()).stream()
                .map(RoleVO::from)
                .toList();
    }

    /** 当前用户的某个私有角色。 */
    public RoleVO get(Long id) {
        return RoleVO.from(requireOwned(id, currentUserId()));
    }

    /** 当前用户创建私有角色时可选的工具。 */
    public List<RoleReferenceValidator.SelectableTool> toolOptions() {
        return referenceValidator.selectableTools(currentUserId());
    }

    /** 创建私有角色：可见性、所有者与 code 由服务端决定。 */
    @Transactional
    public RoleVO create(MyRoleSaveDTO dto) {
        var ownerId = currentUserId();
        referenceValidator.validate(
                RoleVisibilityLevel.PRIVATE, ownerId, dto.skillBindings(), dto.toolWhitelist());
        var entity = new Role();
        entity.setCode(generateCode(ownerId));
        entity.setName(dto.name());
        entity.setDescription(dto.description());
        entity.setSkillIds(RoleSkillBindingJson.write(dto.skillBindings()));
        entity.setToolWhitelist(dto.toolWhitelist());
        entity.setVisibility(RoleVisibilityLevel.PRIVATE);
        entity.setOwnerId(ownerId);
        return RoleVO.from(roleRepository.save(entity));
    }

    /** 更新私有角色内容；状态只由管理端调整，所有者不能改动。 */
    @Transactional
    public RoleVO update(Long id, MyRoleSaveDTO dto) {
        var ownerId = currentUserId();
        var entity = requireOwned(id, ownerId);
        referenceValidator.validate(
                RoleVisibilityLevel.PRIVATE, ownerId, dto.skillBindings(), dto.toolWhitelist());
        entity.setName(dto.name());
        entity.setDescription(dto.description());
        entity.setSkillIds(RoleSkillBindingJson.write(dto.skillBindings()));
        entity.setToolWhitelist(dto.toolWhitelist());
        return RoleVO.from(roleRepository.save(entity));
    }

    /** 软删除私有角色并解除所有者助理上的绑定；它是默认绑定时拒绝，需先切换默认角色。 */
    @Transactional
    public void delete(Long id) {
        var ownerId = currentUserId();
        var entity = requireOwned(id, ownerId);
        var bindings = assistantRoleRepository.findByRoleId(entity.getId());
        if (bindings.stream().anyMatch(binding -> Boolean.TRUE.equals(binding.getIsDefault()))) {
            throw new BusinessException(409, "该角色是助理的默认角色，请先切换默认角色再删除");
        }
        assistantRoleRepository.deleteAll(bindings);
        entity.setDeleted(true);
        entity.setDeleteTime(LocalDateTime.now());
        roleRepository.save(entity);
    }

    private Role requireOwned(Long id, Long ownerId) {
        var role =
                roleRepository
                        .findPrivateByIdAndOwner(id, ownerId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND, NOT_FOUND_MESSAGE));
        if (!RoleVisibility.canModify(ownerId, role)) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, NOT_FOUND_MESSAGE);
        }
        return role;
    }

    private Long currentUserId() {
        return operatorContext
                .currentOwnerId()
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.UNAUTHORIZED));
    }

    /** 服务端生成 code，用户无法指定，也就无法占用 system.role. / public.role. 前缀。 */
    private String generateCode(Long ownerId) {
        var suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return "user.role.%d.%s".formatted(ownerId, suffix);
    }
}
