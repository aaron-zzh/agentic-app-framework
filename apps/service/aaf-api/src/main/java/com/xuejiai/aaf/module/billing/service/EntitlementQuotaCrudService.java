package com.xuejiai.aaf.module.billing.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.crud.ReadonlyCrudService;
import com.xuejiai.aaf.framework.crud.dto.ResourceRefDTO;
import com.xuejiai.aaf.module.billing.domain.EntitlementDef;
import com.xuejiai.aaf.module.billing.domain.EntitlementQuota;
import com.xuejiai.aaf.module.billing.repository.EntitlementDefRepository;
import com.xuejiai.aaf.module.billing.repository.EntitlementQuotaRepository;
import com.xuejiai.aaf.module.billing.vo.EntitlementQuotaPageParam;
import com.xuejiai.aaf.module.billing.vo.EntitlementQuotaVO;
import com.xuejiai.aaf.module.system.file.repository.FileRecordRepository;
import com.xuejiai.aaf.module.system.user.api.UserRelationService;

import lombok.RequiredArgsConstructor;

/** 用户权益额度只读 CRUD 服务。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EntitlementQuotaCrudService
        extends ReadonlyCrudService<
                EntitlementQuota, EntitlementQuotaVO, EntitlementQuotaPageParam> {

    private static final Set<String> SORTABLE_FIELDS =
            Set.of(
                    "id",
                    "userId",
                    "entId",
                    "total",
                    "used",
                    "remain",
                    "nextResetAt",
                    "createTime",
                    "updateTime");

    private final EntitlementQuotaRepository quotaRepository;
    private final EntitlementDefRepository definitionRepository;
    private final FileRecordRepository fileRecordRepository;
    private final UserRelationService userRelationService;

    @Override
    protected EntitlementQuotaRepository getRepository() {
        return quotaRepository;
    }

    @Override
    protected Specification<EntitlementQuota> buildSpec(EntitlementQuotaPageParam request) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (request.getUserId() != null)
                predicates.add(cb.equal(root.get("userId"), request.getUserId()));
            if (request.getEntId() != null)
                predicates.add(cb.equal(root.get("entId"), request.getEntId()));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    @Override
    protected EntitlementQuotaVO toVO(EntitlementQuota quota) {
        return toVOList(List.of(quota), "detail").getFirst();
    }

    @Override
    protected List<EntitlementQuotaVO> toVOList(List<EntitlementQuota> quotas, String fieldSet) {
        if (quotas.isEmpty()) return List.of();
        var users =
                userRelationService.findRefs(
                        quotas.stream()
                                .map(EntitlementQuota::getUserId)
                                .collect(Collectors.toSet()));
        Map<Long, EntitlementDef> definitions =
                definitionRepository
                        .findAllById(
                                quotas.stream()
                                        .map(EntitlementQuota::getEntId)
                                        .collect(Collectors.toSet()))
                        .stream()
                        .collect(Collectors.toMap(EntitlementDef::getId, definition -> definition));
        return quotas.stream()
                .map(
                        quota ->
                                toVO(
                                        quota,
                                        users.get(quota.getUserId()),
                                        definitions.get(quota.getEntId())))
                .toList();
    }

    /** 面向当前用户的额度视图，保留存储额度实时统计语义。 */
    public List<EntitlementQuotaVO> listForUser(Long userId) {
        var storageUsed = fileRecordRepository.sumSizeByUploaderId(userId) / (1024L * 1024 * 1024);
        return toVOList(quotaRepository.findByUserId(userId), "detail").stream()
                .map(
                        quota ->
                                "storage".equals(quota.code())
                                        ? new EntitlementQuotaVO(
                                                quota.id(),
                                                quota.user(),
                                                quota.entitlement(),
                                                quota.code(),
                                                quota.name(),
                                                quota.type(),
                                                quota.unit(),
                                                quota.total(),
                                                storageUsed,
                                                Math.max(0, quota.total() - storageUsed),
                                                quota.nextResetAt())
                                        : quota)
                .toList();
    }

    private EntitlementQuotaVO toVO(
            EntitlementQuota quota, ResourceRefDTO user, EntitlementDef definition) {
        var entitlement =
                definition == null
                        ? null
                        : new ResourceRefDTO(definition.getId(), definition.getName(), null);
        return new EntitlementQuotaVO(
                quota.getId(),
                user,
                entitlement,
                definition == null ? null : definition.getCode(),
                definition == null ? null : definition.getName(),
                definition == null ? null : definition.getType(),
                definition == null ? null : definition.getUnit(),
                quota.getTotal(),
                quota.getUsed(),
                quota.getRemain(),
                quota.getNextResetAt());
    }
}
