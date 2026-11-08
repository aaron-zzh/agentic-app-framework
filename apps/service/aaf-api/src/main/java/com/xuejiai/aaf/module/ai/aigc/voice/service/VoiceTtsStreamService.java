package com.xuejiai.aaf.module.ai.aigc.voice.service;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.engine.cache.ConfigCacheManager;
import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;
import com.xuejiai.aaf.framework.intelligent.ai.speech.SpeechService;
import com.xuejiai.aaf.framework.intelligent.ai.speech.realtime.TtsListener;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRoutingContext;
import com.xuejiai.aaf.framework.security.OperatorContext;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

/**
 * 对话消息流式语音合成：输出 PCM16 16kHz 单声道音频分片。
 *
 * <p>合成前按文本长度预检积分；流正常完成后按预估值结算，出错或客户端取消不结算。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
public class VoiceTtsStreamService {

    private static final String SETTLE_REMARK = "对话语音合成";

    private final SpeechService speechService;
    private final AiCreditGuard creditGuard;
    private final ConfigCacheManager configCacheManager;
    private final OperatorContext operatorContext;

    /**
     * 预检积分后返回合成音频流。预检在调用时同步执行，积分不足直接抛出 {@link
     * com.xuejiai.aaf.common.exception.InsufficientCreditsException}。
     *
     * @param text 待合成文本
     * @param voice 音色编码，为空时使用默认音色
     * @return PCM16 16kHz 单声道音频分片流
     */
    public Flux<byte[]> synthesizeStream(String text, String voice) {
        Long userId =
                operatorContext
                        .currentOwnerId()
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.UNAUTHORIZED, "未登录"));
        var model = configCacheManager.getAiModelByModelId(SpeechService.REALTIME_TTS_MODEL_ID);
        long estimated = speechService.estimateCost(model, text, creditGuard.getMarkupRate());
        creditGuard.precheck(userId, CapabilityRoutingContext.CAP_SPEECH_TTS, estimated);
        return realtimeStream(text, voice)
                .doOnComplete(
                        () ->
                                creditGuard.settleFixedReturningTxId(
                                        userId,
                                        estimated,
                                        CapabilityRoutingContext.CAP_SPEECH_TTS,
                                        SETTLE_REMARK));
    }

    /** 把实时合成任务的回调转为 Flux；下游取消时取消上游任务。 */
    private Flux<byte[]> realtimeStream(String text, String voice) {
        return Flux.create(
                sink -> {
                    var task =
                            speechService.startTtsTask(
                                    text,
                                    voice,
                                    new TtsListener() {
                                        @Override
                                        public void onAudio(byte[] pcm) {
                                            sink.next(pcm);
                                        }

                                        @Override
                                        public void onComplete() {
                                            sink.complete();
                                        }

                                        @Override
                                        public void onError(Throwable error) {
                                            sink.error(error);
                                        }
                                    });
                    sink.onCancel(task::cancel);
                },
                FluxSink.OverflowStrategy.BUFFER);
    }
}
