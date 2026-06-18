package com.xuejiai.aaf.framework.engine.credit;

import java.lang.reflect.Method;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.core.AiCapability;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;
import com.xuejiai.aaf.framework.security.OperatorContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link AiCredit} 切面——统一处理 AI 能力调用的积分预检与结算。
 *
 * <p>约定：方法第一个参数必须是 {@link AiModel}，切面从中读取计费元数据。
 *
 * <p>结算策略（由 {@link AiModel#getQuotaType()} 决定）：
 *
 * <ul>
 *   <li>{@code quotaType == 1}（按次）→ {@link AiCreditGuard#settlePerUse}
 *   <li>其余（按 token）→ 从返回值反射读取 inputTokens/outputTokens → {@link AiCreditGuard#settleByModel}
 * </ul>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AiCreditAspect {

    private final AiCreditGuard creditGuard;
    private final OperatorContext operatorContext;

    @Around("@annotation(aiCredit)")
    public Object around(ProceedingJoinPoint pjp, AiCredit aiCredit) throws Throwable {
        var userId = operatorContext.currentOwnerId().orElse(null);

        // 从第一个参数取 AiModel
        var args = pjp.getArgs();
        AiModel model = (args.length > 0 && args[0] instanceof AiModel m) ? m : null;

        // 能力标识：接口声明优先，注解值作覆写（注解未填或填的是默认值 "ai" 时用接口值）
        Object target = pjp.getTarget();
        String cap = resolveCapability(target, aiCredit);

        // 前置余额预检：委托能力接口估算，markupRate 直接传参
        if (aiCredit.precheck()) {
            int markupRate = creditGuard.getMarkupRate();
            // UNKNOWN_COST=0 表示无法预估，precheck 内部降级为"余额 > 0"保守检查
            final long UNKNOWN_COST = 0L;
            long estimatedCost =
                    (target instanceof AiCapability capability)
                            ? capability.estimateCost(model, args, markupRate)
                            : UNKNOWN_COST;
            creditGuard.precheck(userId, cap, estimatedCost);
        }

        // 执行目标方法
        Object result = pjp.proceed();

        // 成功后结算（流式方法 settle=false，由流末回调手动结算）
        if (aiCredit.settle()) {
            try {
                long[] tokens = extractTokens(result);
                String remark = resolveRemark(target, aiCredit);
                creditGuard.settleByUsage(userId, model, tokens[0], tokens[1], cap, remark);
            } catch (Exception e) {
                log.warn(
                        "@AiCredit 结算失败，不回滚已完成调用: capability={}, userId={}, err={}",
                        cap,
                        userId,
                        e.getMessage());
            }
        }

        return result;
    }

    /** 能力标识决策：接口 capability() 优先，注解 capability 属性作覆写（非默认值 "ai" 时才覆写）。 */
    private String resolveCapability(Object target, AiCredit aiCredit) {
        String annotationCap = aiCredit.capability();
        if (!"ai".equalsIgnoreCase(annotationCap)) {
            return annotationCap; // 注解显式指定，优先
        }
        if (target instanceof AiCapability cap) {
            return cap.capability(); // 接口声明
        }
        return annotationCap;
    }

    /** 业务备注决策：注解 bizName 属性优先，未填时从接口 bizName() 读取。 */
    private String resolveRemark(Object target, AiCredit aiCredit) {
        String annotationRemark = aiCredit.bizName();
        if (!annotationRemark.isEmpty()) {
            return annotationRemark;
        }
        if (target instanceof AiCapability cap) {
            return cap.bizName();
        }
        return null;
    }

    /** 按次计费时估算预检费用：model_price × 10（积分单位），最低 1。 */
    private long estimatePerUseCost(AiModel model) {
        if (model == null || model.getModelPrice() == null) return 1;
        return Math.max(1, Math.round(model.getModelPrice().doubleValue() * 10));
    }

    /** 从返回值反射读取 inputTokens / outputTokens 字段。 */
    private long[] extractTokens(Object result) {
        if (result == null) return new long[] {0, 0};
        try {
            Class<?> cls = result.getClass();
            long input = getLongField(result, cls, "inputTokens");
            long output = getLongField(result, cls, "outputTokens");
            return new long[] {input, output};
        } catch (Exception e) {
            return new long[] {0, 0};
        }
    }

    private long getLongField(Object obj, Class<?> cls, String fieldName) {
        try {
            // record 的 accessor 方法与字段同名
            Method method = cls.getMethod(fieldName);
            Object val = method.invoke(obj);
            return val instanceof Number n ? n.longValue() : 0;
        } catch (NoSuchMethodException e) {
            try {
                var field = cls.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object val = field.get(obj);
                return val instanceof Number n ? n.longValue() : 0;
            } catch (Exception ex) {
                return 0;
            }
        } catch (Exception e) {
            return 0;
        }
    }
}
