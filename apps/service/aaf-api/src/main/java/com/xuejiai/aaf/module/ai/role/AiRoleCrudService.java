package com.xuejiai.aaf.module.ai.role;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.intelligent.assistant.role.AiRoleRepository;
import com.xuejiai.aaf.framework.intelligent.assistant.role.Role;
import com.xuejiai.aaf.framework.intelligent.assistant.role.RoleVisibilityLevel;

import lombok.RequiredArgsConstructor;

/**
 * AI Role 管理端 CRUD 服务。
 *
 * <p>管理端只创建 SYSTEM / PUBLIC 角色，私有角色由用户经 {@code /api/ai/my/roles} 维护。管理员可以查看私有角色用于审查，
 * 但对私有角色只能调整状态（下架或恢复），不能改写内容或删除。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiRoleCrudService
        extends BaseCrudService<Role, RoleVO, RoleCreateDTO, RoleCreateDTO, PageParam> {

    private static final String PUBLIC_CODE_PREFIX = "public.role.";
    private static final Set<String> SORTABLE_FIELDS =
            Set.of("id", "name", "status", "visibility", "createTime", "updateTime");

    private final AiRoleRepository roleRepository;
    private final RoleReferenceValidator referenceValidator;

    @Override
    protected AiRoleRepository getRepository() {
        return roleRepository;
    }

    @Override
    protected RoleVO toVO(Role e) {
        return RoleVO.from(e);
    }

    @Override
    protected Role toEntity(RoleCreateDTO dto) {
        var level = requireManagedLevel(dto.visibility());
        requireCodePrefix(level, dto.code());
        if (roleRepository.existsByCode(dto.code())) {
            throw new BusinessException(409, "Role code 已存在: " + dto.code());
        }
        referenceValidator.validate(level, null, dto.skillBindings(), dto.toolWhitelist());
        var entity = new Role();
        entity.setCode(dto.code());
        entity.setName(dto.name());
        entity.setDescription(dto.description());
        entity.setSkillIds(RoleSkillBindingJson.write(dto.skillBindings()));
        entity.setToolWhitelist(dto.toolWhitelist());
        entity.setKnowledgeBaseIds(RoleKnowledgeBaseJson.write(dto.knowledgeBaseIds()));
        entity.setVisibility(level);
        entity.setStatus(statusOrActive(dto.status()));
        return entity;
    }

    @Override
    protected void updateEntity(Role entity, RoleCreateDTO dto) {
        if (entity.getVisibility() == RoleVisibilityLevel.PRIVATE) {
            // 私有角色只允许调整状态：其余字段必须与现有内容一致
            requireUnchangedPrivateContent(entity, dto);
            entity.setStatus(statusOrActive(dto.status()));
            return;
        }
        if (!Objects.equals(entity.getVisibility(), dto.visibility())) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "Role 可见性创建后不可修改");
        }
        if (!entity.getCode().equals(dto.code())) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "Role code 创建后不可修改");
        }
        referenceValidator.validate(
                entity.getVisibility(), null, dto.skillBindings(), dto.toolWhitelist());
        entity.setName(dto.name());
        entity.setDescription(dto.description());
        entity.setSkillIds(RoleSkillBindingJson.write(dto.skillBindings()));
        entity.setToolWhitelist(dto.toolWhitelist());
        entity.setKnowledgeBaseIds(RoleKnowledgeBaseJson.write(dto.knowledgeBaseIds()));
        entity.setStatus(statusOrActive(dto.status()));
    }

    @Override
    protected void beforeDelete(Role entity) {
        if (entity.getVisibility() == RoleVisibilityLevel.PRIVATE) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "管理端不能删除用户的私有角色，可将其下架");
        }
    }

    /**
     * 管理员把用户的私有角色发布为公开角色：复制为新的 PUBLIC 角色，原私有角色不变。
     *
     * <p>复制时按 PUBLIC 规则校验引用，引用了私有技能则发布失败；每次发布都生成新副本，不记录来源。 请求未指定 code 时由服务端生成。
     */
    @Transactional
    public RoleVO publish(PublishRoleDTO request) {
        var source =
                roleRepository
                        .findById(request.id())
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "Role 不存在"));
        if (source.getVisibility() != RoleVisibilityLevel.PRIVATE) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "只能发布用户的私有角色");
        }
        if (!Role.STATUS_ACTIVE.equals(source.getStatus())) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "已下架的角色不能发布");
        }
        var code =
                request.code() == null || request.code().isBlank()
                        ? PUBLIC_CODE_PREFIX
                                + UUID.randomUUID().toString().replace("-", "").substring(0, 12)
                        : request.code();
        requireCodePrefix(RoleVisibilityLevel.PUBLIC, code);
        if (roleRepository.existsByCode(code)) {
            throw new BusinessException(409, "Role code 已存在: " + code);
        }
        referenceValidator.validate(
                RoleVisibilityLevel.PUBLIC,
                null,
                RoleSkillBindingJson.read(source.getSkillIds()),
                source.getToolWhitelist());
        var copy = new Role();
        copy.setCode(code);
        copy.setName(
                request.name() == null || request.name().isBlank()
                        ? source.getName()
                        : request.name());
        copy.setDescription(source.getDescription());
        copy.setSkillIds(source.getSkillIds());
        copy.setToolWhitelist(source.getToolWhitelist());
        copy.setKnowledgeBaseIds(source.getKnowledgeBaseIds());
        copy.setVisibility(RoleVisibilityLevel.PUBLIC);
        copy.setStatus(Role.STATUS_ACTIVE);
        return RoleVO.from(roleRepository.save(copy));
    }

    /** 管理端只允许创建 SYSTEM / PUBLIC。 */
    static RoleVisibilityLevel requireManagedLevel(RoleVisibilityLevel level) {
        if (level != RoleVisibilityLevel.SYSTEM && level != RoleVisibilityLevel.PUBLIC) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "管理端只能创建 SYSTEM 或 PUBLIC 角色");
        }
        return level;
    }

    /** SYSTEM 使用 system.role.，PUBLIC 使用 public.role.；user.role. 只由服务端为私有角色生成。 */
    static void requireCodePrefix(RoleVisibilityLevel level, String code) {
        var prefix = level == RoleVisibilityLevel.SYSTEM ? "system.role." : "public.role.";
        if (!code.startsWith(prefix) || code.length() == prefix.length()) {
            throw new BusinessException(
                    GlobalErrorCode.BAD_REQUEST, "%s 角色的 code 必须以 %s 开头".formatted(level, prefix));
        }
    }

    private static String statusOrActive(String status) {
        return status == null || status.isBlank() ? Role.STATUS_ACTIVE : status;
    }

    private static void requireUnchangedPrivateContent(Role entity, RoleCreateDTO dto) {
        var unchanged =
                entity.getCode().equals(dto.code())
                        && entity.getName().equals(dto.name())
                        && Objects.equals(entity.getDescription(), dto.description())
                        && Objects.equals(entity.getToolWhitelist(), dto.toolWhitelist())
                        && RoleKnowledgeBaseJson.read(entity.getKnowledgeBaseIds())
                                .equals(RoleKnowledgeBaseJson.read(
                                        RoleKnowledgeBaseJson.write(dto.knowledgeBaseIds())))
                        && RoleSkillBindingJson.read(entity.getSkillIds())
                                .equals(dto.skillBindings())
                        && dto.visibility() == RoleVisibilityLevel.PRIVATE;
        if (!unchanged) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "管理端不能修改用户的私有角色内容，只能调整状态");
        }
    }
}
