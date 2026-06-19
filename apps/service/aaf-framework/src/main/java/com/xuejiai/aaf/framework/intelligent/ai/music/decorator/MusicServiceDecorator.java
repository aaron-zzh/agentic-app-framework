package com.xuejiai.aaf.framework.intelligent.ai.music.decorator;

import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;
import com.xuejiai.aaf.framework.intelligent.ai.music.MusicGenerationService;
import com.xuejiai.aaf.framework.intelligent.core.decorator.AbstractAiServiceDecorator;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;
import com.xuejiai.aaf.framework.security.OperatorContext;

/**
 * 音乐生成服务装饰器——统一处理积分结算（precheck=false）。
 *
 * <p>由 {@link DefaultAiServiceRegistry} 通过 {@link MusicServiceFactory} 路由后手动创建。
 */
public class MusicServiceDecorator extends AbstractAiServiceDecorator<MusicGenerationService>
        implements MusicGenerationService {

    public MusicServiceDecorator(
            MusicGenerationService delegate,
            AiCreditGuard creditGuard,
            OperatorContext operatorContext) {
        super(delegate, creditGuard, operatorContext);
    }

    @Override
    public MusicResult generate(AiModel model, MusicRequest request) {
        return creditCall(model, false, () -> delegate.generate(model, request));
    }
}
