package com.xuejiai.aaf.module.billing.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.framework.crud.dto.ResourceRefDTO;
import com.xuejiai.aaf.framework.engine.credit.CreditService;
import com.xuejiai.aaf.framework.messaging.MessageChannel;
import com.xuejiai.aaf.framework.messaging.MessageRequest;
import com.xuejiai.aaf.framework.messaging.MessageService;
import com.xuejiai.aaf.module.billing.domain.CreditRedeemCode;
import com.xuejiai.aaf.module.billing.domain.SubscriptionSku;
import com.xuejiai.aaf.module.billing.repository.CreditRedeemCodeRepository;
import com.xuejiai.aaf.module.billing.repository.SubscriptionPlanRepository;
import com.xuejiai.aaf.module.billing.repository.SubscriptionSkuRepository;
import com.xuejiai.aaf.module.billing.vo.CreditRedeemCodeCreateDTO;
import com.xuejiai.aaf.module.billing.vo.CreditRedeemCodePageParam;
import com.xuejiai.aaf.module.billing.vo.CreditRedeemCodeVO;
import com.xuejiai.aaf.module.system.user.api.UserRelationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 兑换码管理服务：标准化读模型，写入仅允许显式生成操作。 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreditRedeemCodeService
        extends BaseCrudService<
                CreditRedeemCode,
                CreditRedeemCodeVO,
                CreditRedeemCodeCreateDTO,
                CreditRedeemCodeCreateDTO,
                CreditRedeemCodePageParam> {

    private static final Set<String> SORTABLE_FIELDS =
            Set.of(
                    "id",
                    "codePrefix",
                    "creditAmount",
                    "batchType",
                    "type",
                    "skuId",
                    "status",
                    "expiresAt",
                    "redeemedAt",
                    "createTime",
                    "updateTime");

    private final CreditRedeemCodeRepository redeemCodeRepository;
    private final SubscriptionSkuRepository skuRepository;
    private final SubscriptionPlanRepository planRepository;
    private final UserRelationService userRelationService;
    private final CreditService creditService;
    private final SubscriptionService subscriptionService;
    private final MessageService messageService;

    @Override
    protected CreditRedeemCodeRepository getRepository() {
        return redeemCodeRepository;
    }

    @Override
    protected List<String> optionSearchFields() {
        return List.of("codePrefix");
    }

    @Override
    protected Specification<CreditRedeemCode> buildSpec(CreditRedeemCodePageParam request) {
        var requestedSkuId = request.getSkuId();
        var skuCodeMissing = false;
        if (requestedSkuId == null
                && request.getSkuCode() != null
                && !request.getSkuCode().isBlank()) {
            requestedSkuId =
                    skuRepository
                            .findByCode(request.getSkuCode().trim())
                            .map(SubscriptionSku::getId)
                            .orElse(null);
            skuCodeMissing = requestedSkuId == null;
        }
        var effectiveSkuId = requestedSkuId;
        var missing = skuCodeMissing;
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (request.getStatus() != null && !request.getStatus().isBlank())
                predicates.add(cb.equal(root.get("status"), request.getStatus().trim()));
            if (request.getType() != null && !request.getType().isBlank())
                predicates.add(cb.equal(root.get("type"), request.getType().trim()));
            if (request.getBatchType() != null && !request.getBatchType().isBlank())
                predicates.add(cb.equal(root.get("batchType"), request.getBatchType().trim()));
            if (missing) predicates.add(cb.disjunction());
            else if (effectiveSkuId != null)
                predicates.add(cb.equal(root.get("skuId"), effectiveSkuId));
            if (request.getRedeemedByUserId() != null)
                predicates.add(
                        cb.equal(root.get("redeemedByUserId"), request.getRedeemedByUserId()));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    @Override
    protected CreditRedeemCodeVO toVO(CreditRedeemCode code) {
        return toVOList(List.of(code), "detail").getFirst();
    }

    @Override
    protected List<CreditRedeemCodeVO> toVOList(List<CreditRedeemCode> codes, String fieldSet) {
        if (codes.isEmpty()) return List.of();
        Map<Long, SubscriptionSku> skus =
                skuRepository
                        .findAllById(
                                codes.stream()
                                        .map(CreditRedeemCode::getSkuId)
                                        .filter(java.util.Objects::nonNull)
                                        .collect(Collectors.toSet()))
                        .stream()
                        .collect(Collectors.toMap(SubscriptionSku::getId, sku -> sku));
        Map<Long, com.xuejiai.aaf.module.billing.domain.SubscriptionPlan> plans =
                planRepository
                        .findAllById(
                                skus.values().stream()
                                        .map(SubscriptionSku::getPlanId)
                                        .collect(Collectors.toSet()))
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        com.xuejiai.aaf.module.billing.domain.SubscriptionPlan
                                                ::getId,
                                        plan -> plan));
        var redeemedBy =
                userRelationService.findRefs(
                        codes.stream()
                                .map(CreditRedeemCode::getRedeemedByUserId)
                                .filter(java.util.Objects::nonNull)
                                .collect(Collectors.toSet()));
        return codes.stream()
                .map(
                        code ->
                                toVO(
                                        code,
                                        skus.get(code.getSkuId()),
                                        code.getSkuId() == null
                                                ? null
                                                : plans.get(
                                                        skus.get(code.getSkuId()) == null
                                                                ? null
                                                                : skus.get(code.getSkuId())
                                                                        .getPlanId()),
                                        redeemedBy.get(code.getRedeemedByUserId())))
                .toList();
    }

    @Override
    protected CreditRedeemCode toEntity(CreditRedeemCodeCreateDTO dto) {
        throw writeUnsupported();
    }

    @Override
    protected void updateEntity(CreditRedeemCode entity, CreditRedeemCodeCreateDTO dto) {
        throw writeUnsupported();
    }

    @Override
    @Transactional
    public CreditRedeemCodeVO create(CreditRedeemCodeCreateDTO request) {
        throw writeUnsupported();
    }

    @Override
    @Transactional
    public CreditRedeemCodeVO update(Long id, CreditRedeemCodeCreateDTO request) {
        throw writeUnsupported();
    }

    @Override
    @Transactional
    public void delete(Long id) {
        throw writeUnsupported();
    }

    @Override
    @Transactional
    public void deleteBatch(List<Long> ids) {
        throw writeUnsupported();
    }

    /** 生成单个兑换码并返回仅一次可见的明文。 */
    @Transactional
    public String createAndReturnRawCode(CreditRedeemCodeCreateDTO dto) {
        validateGeneration(dto);
        var rawCode = CreditRedeemSecurityUtil.randomSecret("CRED-", 24);
        redeemCodeRepository.save(createCode(dto, rawCode));
        return rawCode;
    }

    /** 批量生成兑换码并返回所有明文。 */
    @Transactional
    public List<String> createBatch(CreditRedeemCodeCreateDTO dto, int count) {
        validateGeneration(dto);
        if (count < 1 || count > 500) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "批量数量需在 1-500 之间");
        }
        var results = new java.util.ArrayList<String>(count);
        for (var index = 0; index < count; index++) {
            var rawCode = CreditRedeemSecurityUtil.randomSecret("CRED-", 24);
            redeemCodeRepository.save(createCode(dto, rawCode));
            results.add(rawCode);
        }
        return results;
    }

    /** 返回会员码导出所需的受校验 SKU 元数据。 */
    public MembershipSkuInfo membershipSkuInfo(String skuCode) {
        var sku = requireRedeemableSku(skuCode);
        var plan =
                planRepository
                        .findById(sku.getPlanId())
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                com.xuejiai.aaf.module.billing.ErrorCodeConstants
                                                        .SUBSCRIPTION_PLAN_NOT_FOUND));
        return new MembershipSkuInfo(sku.getCode(), plan.getName(), sku.getBillingCycle());
    }

    public record MembershipSkuInfo(String skuCode, String planName, String billingCycle) {}

    /** 用户兑换积分码或会员码。 */
    @Transactional
    @com.xuejiai.aaf.framework.logging.OperationLog(
            module = "兑换码",
            type = com.xuejiai.aaf.framework.logging.OperationType.OTHER,
            description = "兑换码兑换")
    public long redeem(Long userId, String rawCode) {
        var code =
                redeemCodeRepository
                        .findByCodeHashForUpdate(CreditRedeemSecurityUtil.sha256(rawCode))
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "兑换码不存在"));
        if (!"UNUSED".equals(code.getStatus())) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "兑换码已使用或已失效");
        }
        if (code.getExpiresAt() != null && code.getExpiresAt().isBefore(LocalDateTime.now())) {
            code.setStatus("EXPIRED");
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "兑换码已过期");
        }
        var redeemedAt = LocalDateTime.now();
        redeemedAt = redeemedAt.withNano((redeemedAt.getNano() / 1_000) * 1_000);
        if ("MEMBERSHIP".equals(code.getType())) {
            requireRedeemableSku(code.getSkuId());
            subscriptionService.activateGrantedSku(
                    userId, code.getSkuId(), "REDEEM_CODE", code.getId(), redeemedAt);
            code.setStatus("REDEEMED");
            code.setRedeemedByUserId(userId);
            code.setRedeemedAt(redeemedAt);
            notifyDingtalk(
                    "会员兑换", "**会员兑换** \n\n> 用户ID：" + userId + "  \n> 兑换码：" + code.getCodePrefix());
            return 0L;
        }
        creditService.earnBatch(
                userId,
                code.getCreditAmount(),
                code.getBatchType(),
                "redeem_code",
                code.getCodePrefix(),
                null);
        code.setStatus("REDEEMED");
        code.setRedeemedByUserId(userId);
        code.setRedeemedAt(redeemedAt);
        notifyDingtalk(
                "积分兑换",
                "**积分兑换** \n\n> 用户ID："
                        + userId
                        + "  \n> 兑换码："
                        + code.getCodePrefix()
                        + "  \n> 积分："
                        + code.getCreditAmount());
        return code.getCreditAmount();
    }

    private CreditRedeemCode createCode(CreditRedeemCodeCreateDTO dto, String rawCode) {
        var code = new CreditRedeemCode();
        code.setCodeHash(CreditRedeemSecurityUtil.sha256(rawCode));
        code.setCodePrefix(rawCode.substring(0, 10) + "...");
        code.setCreditAmount(dto.creditAmount());
        code.setBatchType(dto.batchType() == null ? "REWARD" : dto.batchType());
        code.setType(dto.type() == null ? "CREDIT" : dto.type());
        if ("MEMBERSHIP".equals(code.getType())) {
            code.setSkuId(requireRedeemableSku(dto.skuCode()).getId());
        }
        code.setExpiresAt(dto.expiresAt());
        code.setRemark(dto.remark());
        return code;
    }

    private void validateGeneration(CreditRedeemCodeCreateDTO dto) {
        if ("MEMBERSHIP".equals(dto.type())) {
            if (dto.skuCode() == null || dto.skuCode().isBlank()) {
                throw new BusinessException(
                        com.xuejiai.aaf.module.billing.ErrorCodeConstants
                                .REDEEM_MEMBERSHIP_SKU_REQUIRED);
            }
            requireRedeemableSku(dto.skuCode());
            if (dto.creditAmount() == null || dto.creditAmount() != 0L) {
                throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "会员码 creditAmount 必须为 0");
            }
            return;
        }
        if (dto.skuCode() != null && !dto.skuCode().isBlank()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "积分码不能指定 skuCode");
        }
        if (dto.creditAmount() == null || dto.creditAmount() < 1) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "积分码的积分数量需 ≥ 1");
        }
    }

    private SubscriptionSku requireRedeemableSku(String skuCode) {
        var sku =
                skuRepository
                        .findByCode(skuCode.trim())
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                com.xuejiai.aaf.module.billing.ErrorCodeConstants
                                                        .SUBSCRIPTION_SKU_NOT_FOUND));
        return requireRedeemableSku(sku);
    }

    private SubscriptionSku requireRedeemableSku(Long skuId) {
        var sku =
                skuRepository
                        .findById(skuId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                com.xuejiai.aaf.module.billing.ErrorCodeConstants
                                                        .SUBSCRIPTION_SKU_NOT_FOUND));
        return requireRedeemableSku(sku);
    }

    private SubscriptionSku requireRedeemableSku(SubscriptionSku sku) {
        if (!"ENABLED".equals(sku.getStatus())) {
            throw new BusinessException(
                    com.xuejiai.aaf.module.billing.ErrorCodeConstants.SUBSCRIPTION_SKU_DISABLED);
        }
        if (sku.isFree() || sku.getPrice() == null || sku.getPrice() <= 0) {
            throw new BusinessException(
                    com.xuejiai.aaf.module.billing.ErrorCodeConstants
                            .SUBSCRIPTION_INTERNAL_SKU_NOT_PURCHASABLE);
        }
        var plan =
                planRepository
                        .findById(sku.getPlanId())
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                com.xuejiai.aaf.module.billing.ErrorCodeConstants
                                                        .SUBSCRIPTION_PLAN_NOT_FOUND));
        if ("FREE".equals(plan.getCode()) || !"ENABLED".equals(plan.getStatus())) {
            throw new BusinessException(
                    com.xuejiai.aaf.module.billing.ErrorCodeConstants
                            .SUBSCRIPTION_SKU_PLAN_INVALID);
        }
        return sku;
    }

    private BusinessException writeUnsupported() {
        return new BusinessException(GlobalErrorCode.BAD_REQUEST, "兑换码仅支持显式生成，不支持通用写入");
    }

    private void notifyDingtalk(String subject, String content) {
        try {
            messageService.send(
                    MessageRequest.direct(
                            MessageChannel.DINGTALK, subject, content, List.of("all")));
        } catch (Exception exception) {
            log.warn("钉钉通知发送失败: subject={}", subject, exception);
        }
    }

    private CreditRedeemCodeVO toVO(
            CreditRedeemCode code,
            SubscriptionSku sku,
            com.xuejiai.aaf.module.billing.domain.SubscriptionPlan plan,
            ResourceRefDTO redeemedBy) {
        return new CreditRedeemCodeVO(
                code.getId(),
                code.getCodePrefix(),
                code.getCreditAmount(),
                code.getBatchType(),
                code.getType(),
                sku == null ? null : new ResourceRefDTO(sku.getId(), sku.getCode(), null),
                sku == null ? null : sku.getCode(),
                sku == null ? null : sku.getBillingCycle(),
                plan == null ? null : new ResourceRefDTO(plan.getId(), plan.getName(), null),
                code.getStatus(),
                code.getExpiresAt(),
                redeemedBy,
                code.getRedeemedAt(),
                code.getRemark(),
                code.getCreateTime());
    }
}
