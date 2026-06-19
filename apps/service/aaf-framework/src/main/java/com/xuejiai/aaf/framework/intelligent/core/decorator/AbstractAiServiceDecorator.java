package com.xuejiai.aaf.framework.intelligent.core.decorator;

import java.util.function.Supplier;

import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;
import com.xuejiai.aaf.framework.intelligent.core.AiCapability;
import com.xuejiai.aaf.framework.intelligent.core.AiUsage;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;
import com.xuejiai.aaf.framework.security.OperatorContext;

import lombok.extern.slf4j.Slf4j;

/**
 * AI 服务装饰器抽象基类。
 *
 * <p>子类持有原始服务 delegate，通过 {@link #creditCall} 模板方法统一处理横切关注点： 积分预检（可选）→ delegate.method() → 积分结算。
 *
 * <p>异常处理：
 *
 * <ul>
 *   <li>delegate 抛异常时不结算
 *   <li>结算本身失败时仅 warn，不回滚已完成调用
 * </ul>
 *
 * <p>流式/异步方法不走 {@link #creditCall}，由各子类直接委托 delegate 并在回调中手动结算。
 */
@Slf4j
public abstract class AbstractAiServiceDecorator<T extends AiCapability> implements AiCapability {

    protected final T delegate;
    protected final AiCreditGuard creditGuard;
    protected final OperatorContext operatorContext;

    protected AbstractAiServiceDecorator(
            T delegate, AiCreditGuard creditGuard, OperatorContext operatorContext) {
        this.delegate = delegate;
        this.creditGuard = creditGuard;
        this.operatorContext = operatorContext;
    }

    @Override
    public String capability() {
        return delegate.capability();
    }

    @Override
    public String bizName() {
        return delegate.bizName();
    }

    /**
     * 同步调用模板：precheck（含 estimateCost 精确预估）→ call → settleByUsage。
     *
     * @param model 已解析的模型（用于积分结算）
     * @param precheck 是否执行前置余额预检
     * @param req 本次调用的请求对象，传给 {@code delegate.estimateCost} 做精确预估；precheck=false 时传 null 即可
     * @param call 实际业务调用
     */
    protected <R> R creditCall(AiModel model, boolean precheck, Object req, Supplier<R> call) {
        // 1. 从安全上下文取当前用户 ID（积分归账依据）
        Long userId = operatorContext.currentOwnerId().orElse(null);

        // 2. [可选] 前置余额预检
        //    delegate 实现了 AiCapability.estimateCost，子类可覆写做精确预估
        //    （如 DashScopeOcrService 按图像尺寸估 token，VideoGenerationService 按分辨率估单元）
        //    estimatedCost=0 时 creditGuard.precheck 降级为"余额 > 0"保守检查
        if (precheck) {
            long estimated = delegate.estimateCost(model, req, creditGuard.getMarkupRate());
            creditGuard.precheck(userId, capability(), estimated);
        }

        // 3. 执行真实 AI 调用（delegate 是无积分逻辑的原始服务实现）
        //    抛异常时跳过结算，不扣积分
        R result = call.get();

        // 4. 调用成功后结算：结果若实现了 AiUsage 接口，直接读取标准化用量
        //    creditGuard.settleByUsage(AiUsage) 内部按 model.quotaType 决定结算方式
        //    结算失败仅 warn，不回滚已完成的 AI 调用
        try {
            AiUsage usage = result instanceof AiUsage u ? u : AiUsage.empty();
            creditGuard.settleByUsage(userId, model, usage, capability(), bizName());
        } catch (Exception e) {
            log.warn(
                    "积分结算失败，不回滚已完成调用: capability={}, userId={}, err={}",
                    capability(),
                    userId,
                    e.getMessage());
        }
        return result;
    }

    /** 同步调用模板（precheck=false 便捷重载）。 */
    protected <R> R creditCall(AiModel model, boolean precheck, Supplier<R> call) {
        return creditCall(model, precheck, null, call);
    }
}
