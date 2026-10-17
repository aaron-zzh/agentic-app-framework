package com.xuejiai.aaf.module.elder.profile.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.elder.profile.ErrorCodeConstants.*;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.elder.AuthorizationScopeEnum;
import com.xuejiai.aaf.common.enums.elder.ElderLivingArrangementEnum;
import com.xuejiai.aaf.common.enums.elder.ElderStatusEnum;
import com.xuejiai.aaf.common.enums.sys.UserSexEnum;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.crud.definition.CrudOperation;
import com.xuejiai.aaf.framework.crud.definition.Patch;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.framework.security.authorization.AuthorizationPlan;
import com.xuejiai.aaf.framework.security.authorization.FunctionPermissionChecker;
import com.xuejiai.aaf.module.elder.profile.api.ElderQueryApi;
import com.xuejiai.aaf.module.elder.profile.domain.Elder;
import com.xuejiai.aaf.module.elder.profile.domain.ElderContact;
import com.xuejiai.aaf.module.elder.profile.mapper.ElderConvert;
import com.xuejiai.aaf.module.elder.profile.repository.ElderContactRepository;
import com.xuejiai.aaf.module.elder.profile.repository.ElderRepository;
import com.xuejiai.aaf.module.elder.profile.vo.ElderContactSummaryVO;
import com.xuejiai.aaf.module.elder.profile.vo.ElderCreateDTO;
import com.xuejiai.aaf.module.elder.profile.vo.ElderPageDTO;
import com.xuejiai.aaf.module.elder.profile.vo.ElderUpdateDTO;
import com.xuejiai.aaf.module.elder.profile.vo.ElderVO;
import com.xuejiai.aaf.module.system.role.relation.GrantRelationDTO;
import com.xuejiai.aaf.module.system.role.relation.ResourceRelationService;

import lombok.RequiredArgsConstructor;

/** 长者档案 CRUD 服务及跨子域只读查询实现。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ElderService
        extends BaseCrudService<Elder, ElderVO, ElderCreateDTO, ElderUpdateDTO, ElderPageDTO>
        implements ElderQueryApi {

    private static final String REBAC_OBJECT_TYPE = "elder";
    private static final String SUBJECT_TYPE_USER = "USER";
    private static final String RELATION_OWNER = "OWNER";
    private static final String RELATION_VIEW_PROFILE = "VIEW_PROFILE";
    private static final String PROFILE_MANAGE_PERMISSION = "elder:profile-elder:create";
    private static final String ALERT_MANAGE_PERMISSION = "elder:guard-alert:update";

    private final ElderRepository elderRepository;
    private final ElderContactRepository elderContactRepository;
    private final ResourceRelationService resourceRelationService;
    private final OperatorContext operatorContext;
    private final FunctionPermissionChecker functionPermissionChecker;

    @Override
    protected ElderRepository getRepository() {
        return elderRepository;
    }

    @Override
    protected ElderVO toVO(Elder entity) {
        return new ElderVO(
                entity.getId(),
                entity.getVersion(),
                entity.getUserId(),
                entity.getFullName(),
                entity.getGender(),
                entity.getBirthDate(),
                entity.getIdCardNo(),
                entity.getMobile(),
                entity.getAddress(),
                entity.getLivingArrangement(),
                entity.getPreferredName(),
                entity.getPreferences(),
                entity.getTaboos(),
                entity.getStatus(),
                entity.getCameraConsent(),
                entity.getCameraConsentBy(),
                entity.getCameraConsentAt(),
                entity.getCreateTime(),
                entity.getUpdateTime(),
                null,
                null,
                contactSummaries(entity.getId()),
                null,
                null,
                null,
                null,
                null);
    }

    @Override
    protected Elder toEntity(ElderCreateDTO request) {
        var entity = ElderConvert.INSTANCE.toEntity(request);
        validate(entity);
        normalizeConsent(entity);
        return entity;
    }

    @Override
    protected void afterCreate(Elder entity, ElderCreateDTO request) {
        synchronizeOwnerRelation(entity.getId(), null, entity.getUserId());
    }

    @Override
    protected void updateEntity(Elder entity, ElderUpdateDTO request) {
        requireExpectedVersion(entity, request.expectedVersion());
        var previousUserId = entity.getUserId();
        applyNullable(request.userId(), entity::setUserId);
        applyRequired(request.fullName(), entity::setFullName);
        applyNullable(request.gender(), entity::setGender);
        applyNullable(request.birthDate(), entity::setBirthDate);
        applyNullable(request.idCardNo(), entity::setIdCardNo);
        applyNullable(request.mobile(), entity::setMobile);
        applyNullable(request.address(), entity::setAddress);
        applyRequired(request.livingArrangement(), entity::setLivingArrangement);
        applyNullable(request.preferredName(), entity::setPreferredName);
        applyNullable(request.preferences(), entity::setPreferences);
        applyNullable(request.taboos(), entity::setTaboos);
        applyRequired(request.status(), entity::setStatus);
        applyRequired(request.cameraConsent(), entity::setCameraConsent);
        applyNullable(request.cameraConsentBy(), entity::setCameraConsentBy);
        applyNullable(request.cameraConsentAt(), entity::setCameraConsentAt);
        validate(entity);
        normalizeConsent(entity);
        entity.setVersion(currentVersion(entity) + 1);
        synchronizeOwnerRelation(entity.getId(), previousUserId, entity.getUserId());
    }

    @Override
    protected void afterDelete(Elder entity) {
        resourceRelationService
                .listByResource(REBAC_OBJECT_TYPE, String.valueOf(entity.getId()))
                .forEach(
                        tuple ->
                                resourceRelationService.revoke(
                                        new GrantRelationDTO(
                                                tuple.objectType(),
                                                tuple.objectId(),
                                                tuple.relation(),
                                                tuple.subjectType(),
                                                tuple.subjectId(),
                                                tuple.subjectRelation(),
                                                tuple.expiresAt())));
    }

    @Override
    protected Specification<Elder> buildSpec(ElderPageDTO request) {
        return SpecificationBuilder.<Elder>builder()
                .eqIfPresent("status", request.getStatus())
                .eqIfPresent("livingArrangement", request.getLivingArrangement())
                .eqIfPresent("userId", request.getUserId())
                .build();
    }

    @Override
    protected List<String> optionSearchFields() {
        return List.of("preferredName", "fullName");
    }

    @Override
    protected String displayName(Elder entity) {
        return entity.getPreferredName() == null || entity.getPreferredName().isBlank()
                ? entity.getFullName()
                : entity.getPreferredName();
    }

    /** 只有具备 VIEW_PROFILE 关系的联系人账号可绕过默认 L3 范围读取档案。 */
    @Override
    protected AuthorizationPlan.RelationRequirement relationRequirement(
            Long id, CrudOperation operation) {
        if (operation != CrudOperation.GET) {
            return null;
        }
        return new AuthorizationPlan.RelationRequirement(
                REBAC_OBJECT_TYPE, String.valueOf(id), RELATION_VIEW_PROFILE);
    }

    @Override
    public ElderOverview requireOverview(Long elderId) {
        var elder = getById(elderId, null, "detail");
        return new ElderOverview(
                elder.id(),
                elder.userId(),
                elder.fullName(),
                elder.preferredName(),
                elder.gender(),
                elder.birthDate(),
                elder.livingArrangement(),
                elder.status());
    }

    @Override
    public AuthorizedElder requireAccess(Long elderId, String authorizationScope) {
        requireAuthorizationScope(authorizationScope);
        var ownerId = currentOwnerId();
        var elder = elderRepository.findById(elderId).orElseThrow(() -> exception(ELDER_NOT_FOUND));
        if (!Objects.equals(elder.getUserId(), ownerId)
                && !isElderOperator(ownerId)
                && !resourceRelationService.hasPermission(
                        ownerId, REBAC_OBJECT_TYPE, String.valueOf(elderId), authorizationScope)) {
            throw exception(GlobalErrorCode.FORBIDDEN);
        }
        return authorizedElder(elder, ownerId);
    }

    @Override
    public List<AuthorizedElder> listAccessibleElders(String authorizationScope) {
        requireAuthorizationScope(authorizationScope);
        var ownerId = currentOwnerId();
        if (isElderOperator(ownerId)) {
            return elderRepository.findAll().stream()
                    .sorted(java.util.Comparator.comparing(Elder::getId))
                    .map(elder -> authorizedElder(elder, ownerId))
                    .toList();
        }
        var elders = new LinkedHashMap<Long, Elder>();
        elderRepository
                .findAllByUserIdOrderByIdAsc(ownerId)
                .forEach(elder -> elders.put(elder.getId(), elder));
        elderContactRepository.findAllByUserIdOrderByElderIdAscPriorityAsc(ownerId).stream()
                .filter(
                        contact ->
                                SetSupport.authorizationScopes(contact.getAuthorizationScopes())
                                        .contains(authorizationScope))
                .map(ElderContact::getElderId)
                .distinct()
                .map(elderRepository::findById)
                .flatMap(java.util.Optional::stream)
                .forEach(elder -> elders.putIfAbsent(elder.getId(), elder));
        return elders.values().stream().map(elder -> authorizedElder(elder, ownerId)).toList();
    }

    private AuthorizedElder authorizedElder(Elder elder, Long ownerId) {
        return new AuthorizedElder(
                toOverview(elder), elder.getOrgId(), elder.getWorkspaceId(), ownerId);
    }

    private ElderOverview toOverview(Elder elder) {
        return new ElderOverview(
                elder.getId(),
                elder.getUserId(),
                elder.getFullName(),
                elder.getPreferredName(),
                elder.getGender(),
                elder.getBirthDate(),
                elder.getLivingArrangement(),
                elder.getStatus());
    }

    private void requireAuthorizationScope(String scope) {
        if (scope == null
                || Arrays.stream(AuthorizationScopeEnum.ARRAYS).noneMatch(scope::equals)) {
            throw exception(GlobalErrorCode.FORBIDDEN);
        }
    }

    private Long currentOwnerId() {
        return operatorContext
                .currentOwnerId()
                .orElseThrow(() -> exception(GlobalErrorCode.UNAUTHORIZED));
    }

    private boolean isElderOperator(Long ownerId) {
        return functionPermissionChecker.hasPermission(ownerId, PROFILE_MANAGE_PERMISSION)
                || functionPermissionChecker.hasPermission(ownerId, ALERT_MANAGE_PERMISSION);
    }

    @Override
    public List<ContactSnapshot> listContacts(Long elderId) {
        requireOverview(elderId);
        return elderContactRepository.findAllByElderIdOrderByPriorityAscIdAsc(elderId).stream()
                .map(this::toContactSnapshot)
                .toList();
    }

    @Override
    public RobotBrief requireRobotBrief(Long elderId) {
        var elder = getById(elderId, null, "detail");
        return new RobotBrief(
                elder.id(),
                elder.preferredName(),
                elder.livingArrangement(),
                elder.preferences(),
                elder.taboos());
    }

    private ContactSnapshot toContactSnapshot(ElderContact contact) {
        return new ContactSnapshot(
                contact.getId(),
                contact.getElderId(),
                contact.getUserId(),
                contact.getName(),
                contact.getMobile(),
                contact.getRelationship(),
                contact.getPriority(),
                contact.getEmergencyContact(),
                SetSupport.authorizationScopes(contact.getAuthorizationScopes()));
    }

    private List<ElderContactSummaryVO> contactSummaries(Long elderId) {
        if (elderId == null) {
            return List.of();
        }
        return elderContactRepository.findAllByElderIdOrderByPriorityAscIdAsc(elderId).stream()
                .map(
                        contact ->
                                new ElderContactSummaryVO(
                                        contact.getId(),
                                        contact.getUserId(),
                                        contact.getName(),
                                        contact.getMobile(),
                                        contact.getRelationship(),
                                        contact.getPriority(),
                                        contact.getEmergencyContact(),
                                        SetSupport.authorizationScopes(
                                                contact.getAuthorizationScopes())))
                .toList();
    }

    private void synchronizeOwnerRelation(Long elderId, Long previousUserId, Long currentUserId) {
        if (Objects.equals(previousUserId, currentUserId)) {
            return;
        }
        if (previousUserId != null) {
            resourceRelationService.revoke(relation(elderId, RELATION_OWNER, previousUserId));
        }
        if (currentUserId != null) {
            resourceRelationService.grant(relation(elderId, RELATION_OWNER, currentUserId));
        }
    }

    private GrantRelationDTO relation(Long elderId, String relation, Long userId) {
        return new GrantRelationDTO(
                REBAC_OBJECT_TYPE,
                String.valueOf(elderId),
                relation,
                SUBJECT_TYPE_USER,
                String.valueOf(userId),
                null,
                null);
    }

    private void validate(Elder entity) {
        requireText(entity.getFullName());
        requireEnum(entity.getLivingArrangement(), ElderLivingArrangementEnum.ARRAYS);
        requireEnum(entity.getStatus(), ElderStatusEnum.ARRAYS);
        if (entity.getGender() != null
                && Arrays.stream(UserSexEnum.ARRAYS)
                        .map(String::valueOf)
                        .noneMatch(entity.getGender()::equals)) {
            throw exception(ELDER_PROFILE_INVALID);
        }
        if (entity.getBirthDate() != null && entity.getBirthDate().isAfter(LocalDate.now())) {
            throw exception(ELDER_PROFILE_INVALID);
        }
        if (Boolean.TRUE.equals(entity.getCameraConsent())
                && (entity.getCameraConsentBy() == null || entity.getCameraConsentAt() == null)) {
            throw exception(ELDER_CONSENT_INVALID);
        }
    }

    private void normalizeConsent(Elder entity) {
        if (!Boolean.TRUE.equals(entity.getCameraConsent())) {
            entity.setCameraConsent(false);
            entity.setCameraConsentBy(null);
            entity.setCameraConsentAt(null);
        }
    }

    private void requireExpectedVersion(Elder entity, Integer expectedVersion) {
        if (expectedVersion == null || expectedVersion != currentVersion(entity)) {
            throw exception(ELDER_VERSION_CONFLICT);
        }
    }

    private int currentVersion(Elder entity) {
        return entity.getVersion() == null ? 0 : entity.getVersion();
    }

    private void requireText(String value) {
        if (value == null || value.isBlank()) {
            throw exception(ELDER_PROFILE_INVALID);
        }
    }

    private void requireEnum(String value, String[] allowedValues) {
        if (value == null || Arrays.stream(allowedValues).noneMatch(value::equals)) {
            throw exception(ELDER_PROFILE_INVALID);
        }
    }

    private <T> void applyRequired(Patch<T> patch, Consumer<T> setter) {
        if (patch.isAbsent()) {
            return;
        }
        if (patch.isNullValue()) {
            throw exception(ELDER_PROFILE_INVALID);
        }
        setter.accept(patch.valueOrNull());
    }

    private <T> void applyNullable(Patch<T> patch, Consumer<T> setter) {
        if (!patch.isAbsent()) {
            setter.accept(patch.valueOrNull());
        }
    }

    /** profile 内部对 JSON 授权范围的唯一解码入口。 */
    private static final class SetSupport {
        private SetSupport() {}

        private static java.util.Set<String> authorizationScopes(String json) {
            return java.util.Set.copyOf(JsonUtils.parseArray(json, String.class));
        }
    }
}
