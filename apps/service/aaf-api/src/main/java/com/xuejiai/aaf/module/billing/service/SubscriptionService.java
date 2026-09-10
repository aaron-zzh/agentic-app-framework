package com.xuejiai.aaf.module.billing.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.billing.ErrorCodeConstants.*;
import static com.xuejiai.aaf.module.system.ErrorCodeConstants.USER_NOT_FOUND;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.enums.billing.SubscriptionOperationEnum;
import com.xuejiai.aaf.common.enums.billing.SubscriptionStatusEnum;
import com.xuejiai.aaf.common.enums.pay.BizOrderTypeEnum;
import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.engine.credit.CreditService;
import com.xuejiai.aaf.module.billing.api.MembershipProvisioningApi;
import com.xuejiai.aaf.module.billing.domain.Subscription;
import com.xuejiai.aaf.module.billing.domain.SubscriptionPlan;
import com.xuejiai.aaf.module.billing.domain.SubscriptionRecord;
import com.xuejiai.aaf.module.billing.domain.SubscriptionSku;
import com.xuejiai.aaf.module.billing.repository.MembershipCheckoutGuardRepository;
import com.xuejiai.aaf.module.billing.repository.SubscriptionPlanRepository;
import com.xuejiai.aaf.module.billing.repository.SubscriptionRecordRepository;
import com.xuejiai.aaf.module.billing.repository.SubscriptionRepository;
import com.xuejiai.aaf.module.billing.repository.SubscriptionSkuRepository;
import com.xuejiai.aaf.module.billing.vo.SubscriptionCheckoutStatusVO;
import com.xuejiai.aaf.module.pay.api.PayOrderQueryApi;
import com.xuejiai.aaf.module.pay.handler.PaySuccessHandler;
import com.xuejiai.aaf.module.pay.service.BizOrderService;
import com.xuejiai.aaf.module.pay.service.PayOrderService;
import com.xuejiai.aaf.module.pay.vo.BizOrderCreateDTO;
import com.xuejiai.aaf.module.pay.vo.PayOrderCreateDTO;
import com.xuejiai.aaf.module.pay.vo.PayOrderVO;
import com.xuejiai.aaf.module.system.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Plan + SKU 订阅服务。所有付费操作只在支付成功回调后生效。 */
@Slf4j
@Service("billingSubscriptionService")
@RequiredArgsConstructor
public class SubscriptionService implements PaySuccessHandler, MembershipProvisioningApi {

    private static final int MONTHLY_CREDIT_EXPIRE_DAYS = 30;
    private static final String PAY_UNPAID = "UNPAID";
    private static final String PAY_PAID = "PAID";
    private static final String FULFILLMENT_PENDING = "PENDING";
    private static final String FULFILLMENT_FULFILLED = "FULFILLED";
    private static final String FULFILLMENT_CLOSED = "CLOSED";
    private static final String FULFILLMENT_COMPENSATION_PENDING = "COMPENSATION_PENDING";
    private static final String VALUE_AVAILABLE = "AVAILABLE";
    private static final String VALUE_SUPERSEDED = "SUPERSEDED";

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionRecordRepository recordRepository;
    private final SubscriptionPlanRepository planRepository;
    private final SubscriptionSkuRepository skuRepository;
    private final MembershipCheckoutGuardRepository checkoutGuardRepository;
    private final BizOrderService bizOrderService;
    private final PayOrderService payOrderService;
    private final PayOrderQueryApi payOrderQueryApi;
    private final EntitlementService entitlementService;
    private final CreditService creditService;
    private final UserRepository userRepository;

    @org.springframework.context.annotation.Lazy
    private final com.xuejiai.aaf.module.brokerage.service.BrokerageService brokerageService;

    @Override
    public String bizOrderType() {
        return BizOrderTypeEnum.SUBSCRIPTION.getCode();
    }

    /** 新购、跨 Plan 升级和同 SKU 手动续费的统一入口。 */
    @Transactional
    public PayOrderVO subscribe(Long userId, String skuCode, String channelCode) {
        var current = lockMembershipUser(userId, true);
        rejectLiveCheckout(userId);

        var targetSku = requireSaleableSku(skuCode);
        var targetPlan = requireEnabledPlan(targetSku.getPlanId());
        var checkoutAt = microsecondNow();
        if (current == null || isFree(current)) {
            return createCheckout(
                    userId,
                    targetPlan,
                    targetSku,
                    SubscriptionOperationEnum.NEW,
                    targetSku.getPrice(),
                    channelCode,
                    current,
                    List.of(),
                    checkoutAt);
        }

        var currentPlan = requirePlan(current.getPlanId());
        if (current.getSkuId().equals(targetSku.getId())) {
            return createCheckout(
                    userId,
                    targetPlan,
                    targetSku,
                    SubscriptionOperationEnum.RENEW,
                    targetSku.getPrice(),
                    channelCode,
                    current,
                    List.of(),
                    checkoutAt);
        }
        if (currentPlan.getId().equals(targetPlan.getId())) {
            throw exception(SUBSCRIPTION_SAME_LEVEL_RENEW_UNSUPPORTED);
        }
        if (targetPlan.getSort() <= currentPlan.getSort()) {
            throw exception(SUBSCRIPTION_DOWNGRADE_ENDPOINT_REQUIRED);
        }

        var calculatedAt = checkoutAt;
        var segments = lockAvailableValueSegments(current, calculatedAt);
        var payable =
                Math.max(0L, targetSku.getPrice() - remainingPrepaidValue(segments, calculatedAt));
        if (payable == 0L) {
            var record =
                    newRecord(
                            userId,
                            targetPlan,
                            targetSku,
                            SubscriptionOperationEnum.UPGRADE,
                            0L,
                            current,
                            segments,
                            calculatedAt);
            record.setPayStatus(PAY_PAID);
            record.setPayTime(calculatedAt);
            recordRepository.save(record);
            activateUpgrade(userId, targetPlan, targetSku, record, segments, calculatedAt, true);
            return null;
        }
        return createCheckout(
                userId,
                targetPlan,
                targetSku,
                SubscriptionOperationEnum.UPGRADE,
                payable,
                channelCode,
                current,
                segments,
                calculatedAt);
    }

    /** 取消只记录意图；当前周期权益保留至到期，不退款。 */
    @Transactional
    public Subscription cancel(Long userId) {
        var sub = requireLockedActive(userId);
        if (sub.getCancelledAt() == null) {
            sub.setCancelledAt(microsecondNow());
            subscriptionRepository.save(sub);
        }
        return sub;
    }

    /** 降级只保存目标 SKU，不支付、不立即改权益。 */
    @Transactional
    public Subscription downgrade(Long userId, String skuCode) {
        var sub = requireLockedActive(userId);
        var currentPlan = requirePlan(sub.getPlanId());
        var targetSku = requireSaleableSku(skuCode);
        var targetPlan = requireEnabledPlan(targetSku.getPlanId());
        if (targetPlan.getId().equals(currentPlan.getId())) {
            throw exception(SUBSCRIPTION_SAME_LEVEL_RENEW_UNSUPPORTED);
        }
        if (targetPlan.getSort() >= currentPlan.getSort()) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "目标套餐不是更低等级");
        }
        sub.setPendingSkuId(targetSku.getId());
        subscriptionRepository.save(sub);
        return sub;
    }

    @Transactional
    public Subscription cancelPending(Long userId) {
        var sub = requireLockedActive(userId);
        sub.setPendingSkuId(null);
        return subscriptionRepository.save(sub);
    }

    /** 管理员按明确 SKU 开通；不创建支付订单。 */
    @Transactional
    public Long activateByAdmin(Long userId, String skuCode) {
        var current = lockMembershipUser(userId, true);
        var sku = requireSaleableSku(skuCode);
        var plan = requireEnabledPlan(sku.getPlanId());
        if (current != null && !isFree(current)) {
            var currentPlan = requirePlan(current.getPlanId());
            if (plan.getId().equals(currentPlan.getId())) {
                throw exception(SUBSCRIPTION_SAME_LEVEL_RENEW_UNSUPPORTED);
            }
            if (plan.getSort() <= currentPlan.getSort()) {
                throw exception(SUBSCRIPTION_UPGRADE_ONLY);
            }
            return activateUpgrade(userId, plan, sku, null, List.of(), microsecondNow(), false);
        }
        return activateSubscriptionInternal(userId, plan, sku, null, false, microsecondNow());
    }

    @Transactional(readOnly = true)
    public Subscription getActiveSubscription(Long userId) {
        return activeSubscription(userId);
    }

    /** 查询当前用户指定会员支付单的履约状态。 */
    @Transactional(readOnly = true)
    public SubscriptionCheckoutStatusVO getCheckoutStatus(Long userId, Long payOrderId) {
        var record =
                recordRepository
                        .findByPayOrderId(payOrderId)
                        .filter(item -> item.getUserId().equals(userId))
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                GlobalErrorCode.NOT_FOUND, "会员支付记录不存在"));
        return new SubscriptionCheckoutStatusVO(
                record.getPayOrderId(),
                record.getPayStatus(),
                record.getFulfillmentStatus(),
                record.getExceptionCode(),
                record.getCompensationResolvedAt(),
                record.getCompensationResult());
    }

    /** 尚无订阅的用户按需初始化 FREE_DEFAULT，不创建支付订单。 */
    @Override
    @Transactional
    public void ensureFreeSubscription(Long userId) {
        var current = lockMembershipUser(userId, true);
        if (current != null) return;
        var freeSku =
                skuRepository
                        .findByCode("FREE_DEFAULT")
                        .orElseThrow(() -> exception(SUBSCRIPTION_SKU_NOT_FOUND, "FREE_DEFAULT"));
        var plan = requireEnabledPlan(freeSku.getPlanId());
        activateSubscriptionInternal(userId, plan, freeSku, null, false, microsecondNow());
    }

    /** 兑换码或系统赠送按明确 SKU 激活，不创建付费 Record。 */
    @Transactional
    public Long activateGrantedSku(
            Long userId, Long skuId, String sourceType, Long sourceId, LocalDateTime effectiveAt) {
        lockMembershipUser(userId, true);
        var sku = requireSaleableSku(skuId);
        var plan = requireEnabledPlan(sku.getPlanId());
        return activateSubscriptionInternal(
                userId, plan, sku, sourceId, false, normalizeMicros(effectiveAt));
    }

    /** 系统按明确 SKU 激活。 */
    @Transactional
    public Long activateSubscription(
            Long userId, Long skuId, Long sourceId, boolean enableBrokerage) {
        lockMembershipUser(userId, true);
        var sku =
                skuRepository
                        .findById(skuId)
                        .orElseThrow(() -> exception(SUBSCRIPTION_SKU_NOT_FOUND, skuId));
        var plan = requirePlan(sku.getPlanId());
        return activateSubscriptionInternal(
                userId, plan, sku, sourceId, enableBrokerage, microsecondNow());
    }

    private Long activateSubscriptionInternal(
            Long userId,
            SubscriptionPlan plan,
            SubscriptionSku sku,
            Long sourceId,
            boolean enableBrokerage,
            LocalDateTime effectiveAt) {
        var existing = activeSubscriptionForUpdate(userId);
        if (existing != null) {
            existing.setStatus(SubscriptionStatusEnum.CANCELLED.getCode());
            existing.setCancelledAt(effectiveAt);
            subscriptionRepository.save(existing);
            subscriptionRepository.flush();
        }
        var subscription = createSubscription(userId, plan, sku, sourceId, effectiveAt);
        entitlementService.instantiateQuotas(userId, plan.getId());
        issueInitialCredits(subscription, plan, effectiveAt);
        if (enableBrokerage && !sku.isFree()) {
            tryEnableBrokerage(userId, plan, sku, subscription.getId());
        }
        return subscription.getId();
    }

    /** 支付成功后才激活、升级或续期；重复回调不会重复处理。 */
    @Override
    @Transactional
    public void onPaySuccess(Long payOrderId) {
        var bizOrder = bizOrderService.findByPayOrderId(payOrderId);
        if (bizOrder == null
                || !BizOrderTypeEnum.SUBSCRIPTION.getCode().equals(bizOrder.getOrderType())) {
            return;
        }
        var initial = recordRepository.findByPayOrderId(payOrderId).orElse(null);
        if (initial == null) return;

        var current = lockMembershipUser(initial.getUserId(), true);
        var record = recordRepository.findByIdForUpdate(initial.getId()).orElse(null);
        if (record == null
                || FULFILLMENT_FULFILLED.equals(record.getFulfillmentStatus())
                || FULFILLMENT_COMPENSATION_PENDING.equals(record.getFulfillmentStatus())) {
            return;
        }
        var payOrder = payOrderQueryApi.findSnapshot(payOrderId).orElse(null);
        if (payOrder == null
                || !com.xuejiai.aaf.common.enums.pay.PayOrderStatusEnum.SUCCESS
                        .getCode()
                        .equals(payOrder.status())) {
            return;
        }

        var effectiveAt =
                normalizeMicros(
                        payOrder.successTime() == null
                                ? LocalDateTime.now()
                                : payOrder.successTime());
        var plan = requirePlan(record.getPlanId());
        var sku =
                skuRepository
                        .findById(record.getSkuId())
                        .orElseThrow(
                                () -> exception(SUBSCRIPTION_SKU_NOT_FOUND, record.getSkuId()));
        var operation = SubscriptionOperationEnum.valueOf(record.getOperation());
        if (FULFILLMENT_CLOSED.equals(record.getFulfillmentStatus())) {
            markCompensation(
                    record,
                    bizOrder.getId(),
                    "CHECKOUT_CLOSED_BUT_PAID",
                    "已关闭结账收到成功款",
                    effectiveAt);
            return;
        }
        var currentGenerationId = current == null ? null : current.getId();
        if (!java.util.Objects.equals(record.getCheckoutGenerationId(), currentGenerationId)) {
            markCompensation(
                    record,
                    bizOrder.getId(),
                    "OPERATION_STATE_CHANGED",
                    "支付成功时当前订阅世代已不同于下单世代",
                    effectiveAt);
            return;
        }
        var segments =
                operation == SubscriptionOperationEnum.UPGRADE && current != null
                        ? lockAvailableValueSegments(current, effectiveAt)
                        : List.<SubscriptionRecord>of();
        var targetPriceSnapshot = record.getSkuPriceSnapshot();
        var expectedAmount =
                operation == SubscriptionOperationEnum.UPGRADE
                        ? Math.max(
                                0L,
                                targetPriceSnapshot - remainingPrepaidValue(segments, effectiveAt))
                        : targetPriceSnapshot;

        if (!canApply(record, current)) {
            var duplicateSuccess = isDuplicateSuccess(record, current);
            markCompensation(
                    record,
                    bizOrder.getId(),
                    duplicateSuccess ? "DUPLICATE_SUCCESS" : "OPERATION_STATE_CHANGED",
                    duplicateSuccess ? "另一支付单已先完成相同目标履约" : "支付成功时订阅世代或操作状态已变化",
                    effectiveAt);
            return;
        }
        if (!java.util.Objects.equals(payOrder.amount(), record.getPayPrice())
                || expectedAmount != record.getPayPrice()) {
            markCompensation(
                    record,
                    bizOrder.getId(),
                    "AMOUNT_SNAPSHOT_CHANGED",
                    "回调重算金额与支付订单快照不一致",
                    effectiveAt);
            return;
        }

        record.setPayStatus(PAY_PAID);
        record.setPayTime(effectiveAt);
        switch (operation) {
            case NEW -> activateNew(record, plan, sku, effectiveAt);
            case UPGRADE ->
                    activateUpgrade(
                            record.getUserId(), plan, sku, record, segments, effectiveAt, true);
            case RENEW -> activateRenew(record, current, sku, effectiveAt);
        }
        recordRepository.save(record);
        bizOrderService.markPaid(bizOrder.getId());
    }

    /** 到期扫描兜底。 */
    @Transactional
    public int expireSubscriptions() {
        var candidates =
                subscriptionRepository.findByStatusAndEndAtBefore(
                        SubscriptionStatusEnum.ACTIVE.getCode(), microsecondNow());
        var expired = 0;
        for (var candidate : candidates) {
            if (expireAndSwitchToFree(candidate.getUserId(), candidate.getId())) {
                expired++;
            }
        }
        return expired;
    }

    /** 在统一会员锁序内结束指定到期世代，并按 pending FREE 或 FREE_DEFAULT 激活兜底订阅。 */
    @Transactional
    public boolean expireAndSwitchToFree(Long userId, Long expectedSubscriptionId) {
        var effectiveAt = microsecondNow();
        var current = lockMembershipUser(userId, false);
        if (current == null
                || !current.getId().equals(expectedSubscriptionId)
                || current.getEndAt() == null
                || current.getEndAt().isAfter(effectiveAt)) {
            return false;
        }

        var freeSku =
                java.util.Optional.ofNullable(current.getPendingSkuId())
                        .flatMap(skuRepository::findById)
                        .filter(SubscriptionSku::isFree)
                        .filter(sku -> "ENABLED".equals(sku.getStatus()))
                        .orElseGet(
                                () ->
                                        skuRepository
                                                .findByCode("FREE_DEFAULT")
                                                .filter(sku -> "ENABLED".equals(sku.getStatus()))
                                                .orElse(null));

        current.setStatus(SubscriptionStatusEnum.EXPIRED.getCode());
        subscriptionRepository.save(current);
        subscriptionRepository.flush();
        if (freeSku == null) {
            log.warn("FREE_DEFAULT 不存在或未启用，用户到期后暂不激活免费订阅: userId={}", userId);
            return true;
        }

        var freePlan = requireEnabledPlan(freeSku.getPlanId());
        activateSubscriptionInternal(userId, freePlan, freeSku, null, false, effectiveAt);
        return true;
    }

    private PayOrderVO createCheckout(
            Long userId,
            SubscriptionPlan plan,
            SubscriptionSku sku,
            SubscriptionOperationEnum operation,
            long payable,
            String channelCode,
            Subscription current,
            List<SubscriptionRecord> valueSegments,
            LocalDateTime calculatedAt) {
        String action =
                switch (operation) {
                    case NEW -> "订阅 ";
                    case UPGRADE -> "订阅升级 ";
                    case RENEW -> "订阅续费 ";
                };
        String label = plan.getName() + "（" + cycleLabel(sku) + "）";
        var bizOrder =
                bizOrderService.create(
                        userId,
                        new BizOrderCreateDTO(
                                BizOrderTypeEnum.SUBSCRIPTION.getCode(),
                                action + label,
                                payable,
                                channelCode));
        var payOrder =
                payOrderService.create(
                        new PayOrderCreateDTO(
                                bizOrder.orderNo(),
                                action + label,
                                null,
                                payable,
                                channelCode,
                                userId));
        bizOrderService.bindPayOrder(bizOrder.id(), payOrder.id());
        var record =
                newRecord(
                        userId,
                        plan,
                        sku,
                        operation,
                        payable,
                        current,
                        valueSegments,
                        calculatedAt);
        record.setPayOrderId(payOrder.id());
        record.setPayStatus(PAY_UNPAID);
        recordRepository.save(record);
        if (payOrderQueryApi
                .findSnapshot(payOrder.id())
                .filter(
                        snapshot ->
                                com.xuejiai.aaf.common.enums.pay.PayOrderStatusEnum.SUCCESS
                                        .getCode()
                                        .equals(snapshot.status()))
                .isPresent()) {
            onPaySuccess(payOrder.id());
        }
        return payOrder;
    }

    private SubscriptionRecord newRecord(
            Long userId,
            SubscriptionPlan plan,
            SubscriptionSku sku,
            SubscriptionOperationEnum operation,
            long payable,
            Subscription current,
            List<SubscriptionRecord> valueSegments,
            LocalDateTime calculatedAt) {
        var record = new SubscriptionRecord();
        record.setUserId(userId);
        record.setPlanId(plan.getId());
        record.setSkuId(sku.getId());
        record.setOperation(operation.getCode());
        record.setPayPrice(payable);
        record.setSkuPriceSnapshot(sku.getPrice());
        record.setPayStatus(PAY_UNPAID);
        record.setFulfillmentStatus(FULFILLMENT_PENDING);
        record.setCheckoutGenerationId(current == null ? null : current.getId());
        record.setCheckoutCalculatedAt(calculatedAt);
        if (operation == SubscriptionOperationEnum.UPGRADE) {
            record.setCheckoutValueSnapshot(checkoutValueSnapshot(valueSegments, calculatedAt));
        }
        return record;
    }

    private void activateNew(
            SubscriptionRecord record,
            SubscriptionPlan plan,
            SubscriptionSku sku,
            LocalDateTime effectiveAt) {
        var id =
                activateSubscriptionInternal(
                        record.getUserId(), plan, sku, record.getId(), true, effectiveAt);
        var subscription = subscriptionRepository.findById(id).orElseThrow();
        bindServiceSegment(record, subscription, effectiveAt, subscription.getEndAt());
    }

    private Long activateUpgrade(
            Long userId,
            SubscriptionPlan plan,
            SubscriptionSku sku,
            SubscriptionRecord record,
            List<SubscriptionRecord> segments,
            LocalDateTime effectiveAt,
            boolean enableBrokerage) {
        var old = requireActive(userId);
        if (record != null) {
            for (var segment : segments) {
                segment.setValueStatus(VALUE_SUPERSEDED);
                segment.setSupersededByRecordId(record.getId());
                segment.setSupersededAt(effectiveAt);
                recordRepository.save(segment);
            }
        }
        old.setStatus(SubscriptionStatusEnum.CANCELLED.getCode());
        old.setCancelledAt(effectiveAt);
        subscriptionRepository.save(old);
        subscriptionRepository.flush();

        var newSub =
                createSubscription(
                        userId, plan, sku, record == null ? null : record.getId(), effectiveAt);
        if (record != null) {
            bindServiceSegment(record, newSub, effectiveAt, newSub.getEndAt());
        }
        entitlementService.instantiateQuotas(userId, plan.getId());
        if (plan.getMonthlyCredits() != null && plan.getMonthlyCredits() > 0) {
            creditService.settleSubscriptionUpgrade(
                    userId,
                    plan.getMonthlyCredits(),
                    newSub.getId(),
                    effectiveAt.plusDays(MONTHLY_CREDIT_EXPIRE_DAYS));
            newSub.setLastCreditIssuedAt(effectiveAt);
            subscriptionRepository.save(newSub);
        }
        if (enableBrokerage) {
            tryEnableBrokerage(userId, plan, sku, newSub.getId());
        }
        return newSub.getId();
    }

    private void activateRenew(
            SubscriptionRecord record,
            Subscription current,
            SubscriptionSku sku,
            LocalDateTime effectiveAt) {
        var serviceStart = current.getEndAt();
        if (serviceStart == null || serviceStart.isBefore(effectiveAt)) {
            serviceStart = effectiveAt;
        }
        var serviceEnd = serviceStart.plusMonths(sku.getCycleMonths());
        current.setEndAt(serviceEnd);
        current.setSourceId(record.getId());
        current.setCancelledAt(null);
        subscriptionRepository.save(current);
        bindServiceSegment(record, current, serviceStart, serviceEnd);
    }

    private Subscription createSubscription(
            Long userId,
            SubscriptionPlan plan,
            SubscriptionSku sku,
            Long sourceId,
            LocalDateTime start) {
        var subscription = new Subscription();
        subscription.setUserId(userId);
        subscription.setPlanId(plan.getId());
        subscription.setSkuId(sku.getId());
        subscription.setStartAt(start);
        subscription.setEndAt(sku.isFree() ? null : start.plusMonths(sku.getCycleMonths()));
        subscription.setStatus(SubscriptionStatusEnum.ACTIVE.getCode());
        subscription.setSourceId(sourceId);
        return subscriptionRepository.save(subscription);
    }

    private void bindServiceSegment(
            SubscriptionRecord record,
            Subscription subscription,
            LocalDateTime start,
            LocalDateTime end) {
        record.setServiceGenerationId(subscription.getId());
        record.setServiceStartAt(normalizeMicros(start));
        record.setServiceEndAt(normalizeMicros(end));
        record.setValueStatus(VALUE_AVAILABLE);
        record.setFulfillmentStatus(FULFILLMENT_FULFILLED);
        recordRepository.save(record);
    }

    private List<SubscriptionRecord> lockAvailableValueSegments(
            Subscription current, LocalDateTime calculatedAt) {
        return recordRepository.findAvailableValueSegmentsForUpdate(
                current.getUserId(), current.getId(), calculatedAt);
    }

    private long remainingPrepaidValue(
            List<SubscriptionRecord> segments, LocalDateTime calculatedAt) {
        long total = 0L;
        for (var segment : segments) {
            total = Math.addExact(total, remainingSegmentValue(segment, calculatedAt));
        }
        return total;
    }

    private long remainingSegmentValue(SubscriptionRecord segment, LocalDateTime calculatedAt) {
        if (segment.getServiceStartAt() == null
                || segment.getServiceEndAt() == null
                || !segment.getServiceEndAt().isAfter(calculatedAt)) {
            return 0L;
        }
        var overlapStart =
                segment.getServiceStartAt().isAfter(calculatedAt)
                        ? segment.getServiceStartAt()
                        : calculatedAt;
        long segmentMicros = microsBetween(segment.getServiceStartAt(), segment.getServiceEndAt());
        long remainMicros = microsBetween(overlapStart, segment.getServiceEndAt());
        if (segmentMicros <= 0L || remainMicros <= 0L) return 0L;
        return BigDecimal.valueOf(segment.getSkuPriceSnapshot())
                .multiply(BigDecimal.valueOf(remainMicros))
                .divide(BigDecimal.valueOf(segmentMicros), 0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    private String checkoutValueSnapshot(
            List<SubscriptionRecord> segments, LocalDateTime calculatedAt) {
        var values =
                segments.stream()
                        .map(
                                segment ->
                                        new CheckoutValueSegment(
                                                segment.getId(),
                                                remainingSegmentValue(segment, calculatedAt)))
                        .toList();
        var total = values.stream().mapToLong(CheckoutValueSegment::remainingValue).sum();
        return JsonUtils.toJsonString(new CheckoutValueSnapshot(calculatedAt, total, values));
    }

    private record CheckoutValueSegment(Long recordId, long remainingValue) {}

    private record CheckoutValueSnapshot(
            LocalDateTime calculatedAt, long total, List<CheckoutValueSegment> segments) {}

    private long microsBetween(LocalDateTime start, LocalDateTime end) {
        return Duration.between(normalizeMicros(start), normalizeMicros(end)).toNanos() / 1_000L;
    }

    private boolean isDuplicateSuccess(SubscriptionRecord record, Subscription current) {
        return current != null
                && current.getSourceId() != null
                && !current.getSourceId().equals(record.getId())
                && current.getSkuId().equals(record.getSkuId());
    }

    private boolean canApply(SubscriptionRecord record, Subscription current) {
        var operation = SubscriptionOperationEnum.valueOf(record.getOperation());
        if (operation == SubscriptionOperationEnum.NEW) {
            return current == null || isFree(current);
        }
        if (current == null || isFree(current)) {
            return false;
        }
        if (operation == SubscriptionOperationEnum.RENEW) {
            return current.getSkuId().equals(record.getSkuId());
        }
        var currentPlan = requirePlan(current.getPlanId());
        var targetPlan = requirePlan(record.getPlanId());
        return targetPlan.getSort() > currentPlan.getSort();
    }

    private void rejectLiveCheckout(Long userId) {
        var pending =
                recordRepository.findLiveByUserIdForUpdate(userId).stream()
                        .findFirst()
                        .orElse(null);
        if (pending == null) return;
        if (pending.getPayOrderId() != null
                && payOrderQueryApi.isLive(pending.getPayOrderId(), microsecondNow())) {
            throw new BusinessException(
                    SUBSCRIPTION_PENDING_PAYMENT_EXISTS,
                    "已有待支付会员订单，payOrderId=" + pending.getPayOrderId());
        }
        pending.setFulfillmentStatus(FULFILLMENT_CLOSED);
        recordRepository.save(pending);
    }

    private SubscriptionSku requireSaleableSku(String code) {
        return requireSaleableSku(requireEnabledSku(code));
    }

    private SubscriptionSku requireSaleableSku(Long skuId) {
        var sku =
                skuRepository
                        .findById(skuId)
                        .orElseThrow(() -> exception(SUBSCRIPTION_SKU_NOT_FOUND, skuId));
        if (!"ENABLED".equals(sku.getStatus())) {
            throw exception(SUBSCRIPTION_SKU_DISABLED);
        }
        return requireSaleableSku(sku);
    }

    private SubscriptionSku requireSaleableSku(SubscriptionSku sku) {
        if (sku.isFree() || sku.getPrice() == null || sku.getPrice() <= 0) {
            throw exception(SUBSCRIPTION_INTERNAL_SKU_NOT_PURCHASABLE);
        }
        return sku;
    }

    private SubscriptionSku requireEnabledSku(String code) {
        var sku =
                skuRepository
                        .findByCode(code)
                        .orElseThrow(() -> exception(SUBSCRIPTION_SKU_NOT_FOUND, code));
        if (!"ENABLED".equals(sku.getStatus())) {
            throw exception(SUBSCRIPTION_SKU_DISABLED);
        }
        return sku;
    }

    private SubscriptionPlan requireEnabledPlan(Long planId) {
        var plan = requirePlan(planId);
        if (!"ENABLED".equals(plan.getStatus())) {
            throw exception(SUBSCRIPTION_PLAN_DISABLED);
        }
        return plan;
    }

    private SubscriptionPlan requirePlan(Long planId) {
        return planRepository
                .findById(planId)
                .orElseThrow(() -> exception(SUBSCRIPTION_PLAN_NOT_FOUND, planId));
    }

    private void markCompensation(
            SubscriptionRecord record,
            Long bizOrderId,
            String exceptionCode,
            String reason,
            LocalDateTime detectedAt) {
        record.setPayStatus(PAY_PAID);
        record.setPayTime(detectedAt);
        record.setFulfillmentStatus(FULFILLMENT_COMPENSATION_PENDING);
        record.setValueStatus(null);
        record.setExceptionCode(exceptionCode);
        record.setExceptionReason(reason);
        record.setExceptionDetectedAt(detectedAt);
        recordRepository.save(record);
        bizOrderService.markCompensationPending(bizOrderId);
        log.error(
                "订阅支付进入人工补偿: userId={}, payOrderId={}, code={}",
                record.getUserId(),
                record.getPayOrderId(),
                exceptionCode);
    }

    private Subscription requireLockedActive(Long userId) {
        var subscription = lockMembershipUser(userId, true);
        if (subscription == null) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "无生效订阅");
        }
        return subscription;
    }

    private Subscription lockMembershipUser(Long userId, boolean requireUser) {
        checkoutGuardRepository.ensureGuard(userId);
        checkoutGuardRepository.lockGuard(userId);
        if (requireUser && userRepository.findById(userId).isEmpty()) {
            throw exception(USER_NOT_FOUND);
        }
        return activeSubscriptionForUpdate(userId);
    }

    private Subscription activeSubscriptionForUpdate(Long userId) {
        return subscriptionRepository
                .findByUserIdAndStatusForUpdate(userId, SubscriptionStatusEnum.ACTIVE.getCode())
                .orElse(null);
    }

    private LocalDateTime microsecondNow() {
        return normalizeMicros(LocalDateTime.now());
    }

    private LocalDateTime normalizeMicros(LocalDateTime value) {
        if (value == null) return null;
        return value.withNano((value.getNano() / 1_000) * 1_000);
    }

    private Subscription requireActive(Long userId) {
        var subscription = activeSubscription(userId);
        if (subscription == null) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND, "无生效订阅");
        }
        return subscription;
    }

    private Subscription activeSubscription(Long userId) {
        return subscriptionRepository
                .findByUserIdAndStatus(userId, SubscriptionStatusEnum.ACTIVE.getCode())
                .orElse(null);
    }

    private boolean isFree(Subscription subscription) {
        return skuRepository
                .findById(subscription.getSkuId())
                .map(SubscriptionSku::isFree)
                .orElse(false);
    }

    private void issueInitialCredits(
            Subscription subscription, SubscriptionPlan plan, LocalDateTime now) {
        if (plan.getMonthlyCredits() == null || plan.getMonthlyCredits() <= 0) {
            return;
        }
        creditService.earnBatch(
                subscription.getUserId(),
                plan.getMonthlyCredits(),
                "SUBSCRIPTION",
                "SUBSCRIPTION_ACTIVATE",
                String.valueOf(subscription.getId()),
                now.plusDays(MONTHLY_CREDIT_EXPIRE_DAYS));
        subscription.setLastCreditIssuedAt(now);
        subscriptionRepository.save(subscription);
    }

    private String cycleLabel(SubscriptionSku sku) {
        return switch (sku.getBillingCycle()) {
            case "MONTH" -> "月付";
            case "QUARTER" -> "季付";
            case "YEAR" -> "年付";
            default -> "免费";
        };
    }

    private void tryEnableBrokerage(
            Long userId, SubscriptionPlan plan, SubscriptionSku sku, Long subscriptionId) {
        try {
            userRepository
                    .findById(userId)
                    .ifPresent(
                            user -> {
                                if (user.getContactId() != null) {
                                    brokerageService.tryEnableBrokerage(
                                            user.getContactId(), "PAID");
                                    brokerageService.calculateBrokerage(
                                            user.getContactId(),
                                            "SUBSCRIBE",
                                            "SKU",
                                            String.valueOf(sku.getId()),
                                            String.valueOf(subscriptionId),
                                            "订阅 " + plan.getName() + "（" + cycleLabel(sku) + "）",
                                            sku.getPrice());
                                }
                            });
        } catch (Exception e) {
            log.warn("分销资格自动开通失败，不影响订阅流程: userId={}", userId, e);
        }
    }
}
