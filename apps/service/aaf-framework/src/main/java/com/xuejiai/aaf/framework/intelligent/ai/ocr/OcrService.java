package com.xuejiai.aaf.framework.intelligent.ai.ocr;

import com.xuejiai.aaf.framework.intelligent.ai.ocr.vo.OcrRequest;
import com.xuejiai.aaf.framework.intelligent.ai.ocr.vo.OcrResult;
import com.xuejiai.aaf.framework.intelligent.core.AiCapability;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRoutingContext;

import io.reactivex.Flowable;

/** OCR 文字提取服务接口，支持通用识别、信息抽取、表格解析等内置任务。 */
public interface OcrService extends AiCapability {

    @Override
    default String capability() {
        return CapabilityRoutingContext.CAP_OCR;
    }

    @Override
    default String bizName() {
        return "OCR 识别";
    }

    /** 供实现类回退到 AiCapability 默认估算逻辑（Java 不允许跨层 super 调用）。 */
    default long defaultEstimateCost(AiModel model, Object[] args, int markupRate) {
        return AiCapability.super.estimateCost(model, args, markupRate);
    }

    /**
     * 阻塞式 OCR 识别。
     *
     * <p>约定：切面从第一个参数 {@link AiModel} 读取计费元数据，调用方须先走 {@code CapabilityRouter} 决策。
     */
    OcrResult recognize(AiModel model, OcrRequest request);

    /**
     * 流式 OCR 识别，返回文本片段 Flowable。
     *
     * <p>约定同 {@link #recognize}，切面计费从第一个参数读取。
     */
    Flowable<String> streamRecognize(AiModel model, OcrRequest request);
}
