package com.xuejiai.aaf.framework.engine.credit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * AI 能力积分门控注解。
 *
 * <p>标注在 AI 能力服务方法上，切面自动完成：
 *
 * <ol>
 *   <li>前置余额预检（{@link AiCreditGuard#precheck}）
 *   <li>执行目标方法
 *   <li>成功后按模型 quotaType 统一结算（{@link AiCreditGuard#settleByUsage}）： quotaType=1 按次，其余按 token
 *   <li>方法抛异常时不扣减
 * </ol>
 *
 * <p><b>切面实现</b>：{@link AiCreditAspect}，通过 {@code @Around("@annotation(AiCredit)")} 拦截。
 *
 * <p><b>约定</b>：方法第一个参数必须是 {@link com.xuejiai.aaf.framework.intelligent.core.model.AiModel}（已通过
 * {@code CapabilityRouter} 决策链解析），切面从中读取 quotaType / modelPrice / id 等计费元数据。
 *
 * <p><b>结算策略</b>：返回值中若包含 {@code inputTokens} / {@code outputTokens} 字段（如 {@code OcrResult}），切面自动用实际
 * token 数结算；字段不存在时 token 均为 0，由 {@link AiCreditGuard#settleByUsage} 内部按 quotaType 决定最终行为。
 *
 * <p><b>工具调用链路</b>：积分不足时切面抛 {@code InsufficientCreditsException}，由 {@code ToolPermissionGuard}
 * catch 后转为结构化 {@code ToolCallResult.insufficientCredits}， LLM 可感知并在对话中通知用户。
 *
 * <p><b>适用场景</b>：同步 AI 能力（OCR、文本生成等）。异步任务（图像/视频生成）只做前置 precheck，结算在异步回调中直接调用 {@link
 * AiCreditGuard#settleByUsage}。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AiCredit {

    /** 能力标识，用于积分流水记录（如 "ocr" / "image-gen"）。 */
    String capability() default "ai";

    /** 业务名称，写入积分流水 remark 字段，便于用户查看具体消耗来源（如 "OCR 识别"）。 */
    String bizName() default "";

    /** 是否执行前置余额预检，默认 true。 */
    boolean precheck() default true;

    /**
     * 是否由切面自动结算，默认 true。
     *
     * <p>流式方法（返回 Flowable/Flux）应设为 false，在流末回调中手动调用 {@link AiCreditGuard#settleByUsage}， 切面仅负责前置预检。
     */
    boolean settle() default true;
}
