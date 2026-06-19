package com.xuejiai.aaf.framework.intelligent.ai.ocr.decorator;

import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;
import com.xuejiai.aaf.framework.intelligent.ai.ocr.OcrService;
import com.xuejiai.aaf.framework.intelligent.ai.ocr.vo.OcrRequest;
import com.xuejiai.aaf.framework.intelligent.ai.ocr.vo.OcrResult;
import com.xuejiai.aaf.framework.intelligent.core.decorator.AbstractAiServiceDecorator;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;
import com.xuejiai.aaf.framework.security.OperatorContext;

import io.reactivex.Flowable;

/**
 * OCR 服务装饰器——同步 recognize 包积分（precheck=true），流式直接委托。
 *
 * <p>由 {@link com.xuejiai.aaf.framework.intelligent.core.registry.DefaultAiServiceRegistry} 通过
 * {@link com.xuejiai.aaf.framework.intelligent.ai.ocr.OcrServiceFactory} 路由后手动创建。
 */
public class OcrServiceDecorator extends AbstractAiServiceDecorator<OcrService>
        implements OcrService {

    public OcrServiceDecorator(
            OcrService delegate, AiCreditGuard creditGuard, OperatorContext operatorContext) {
        super(delegate, creditGuard, operatorContext);
    }

    @Override
    public OcrResult recognize(AiModel model, OcrRequest request) {
        return creditCall(model, true, request, () -> delegate.recognize(model, request));
    }

    @Override
    public Flowable<String> streamRecognize(AiModel model, OcrRequest request) {
        // 流式方法不包积分，由流末回调手动结算
        return delegate.streamRecognize(model, request);
    }
}
