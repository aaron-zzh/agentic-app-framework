package com.xuejiai.aaf.framework.intelligent.ai.speech.decorator;

import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;
import com.xuejiai.aaf.framework.intelligent.ai.speech.AsrResult;
import com.xuejiai.aaf.framework.intelligent.ai.speech.SpeechService;
import com.xuejiai.aaf.framework.intelligent.ai.speech.SynthesisResult;
import com.xuejiai.aaf.framework.intelligent.core.decorator.AbstractAiServiceDecorator;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;
import com.xuejiai.aaf.framework.security.OperatorContext;

import reactor.core.publisher.Flux;

/**
 * 语音合成（TTS）服务装饰器，统一处理积分结算（precheck=false）。
 *
 * <p>仅包装 {@link #synthesize}（任务型配音）；ASR 路径不经过此装饰器，积分在 AsrWebSocketHandler 手动结算。
 *
 * <p>由 {@link com.xuejiai.aaf.framework.intelligent.core.registry.DefaultAiServiceRegistry} 通过
 * {@link SpeechServiceFactory} 路由后动态创建。
 */
public class SpeechServiceDecorator extends AbstractAiServiceDecorator<SpeechService>
        implements SpeechService {

    public SpeechServiceDecorator(
            SpeechService delegate, AiCreditGuard creditGuard, OperatorContext operatorContext) {
        super(delegate, creditGuard, operatorContext);
    }

    @Override
    public SynthesisResult synthesize(AiModel model, String text, String voice) {
        // precheck=false：预检已在 AigcTaskService 完成，此处只结算
        return creditCall(model, false, () -> delegate.synthesize(model, text, voice));
    }

    @Override
    public String transcribe(byte[] audioBytes, String language) {
        return delegate.transcribe(audioBytes, language);
    }

    @Override
    public Flux<AsrResult> transcribeStream(Flux<byte[]> audioStream, String language) {
        return delegate.transcribeStream(audioStream, language);
    }

    @Override
    public Flux<byte[]> synthesizeStream(String text, String voice) {
        return delegate.synthesizeStream(text, voice);
    }
}
