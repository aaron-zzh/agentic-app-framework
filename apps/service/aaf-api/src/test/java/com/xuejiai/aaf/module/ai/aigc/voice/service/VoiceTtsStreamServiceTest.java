package com.xuejiai.aaf.module.ai.aigc.voice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.InsufficientCreditsException;
import com.xuejiai.aaf.framework.engine.cache.ConfigCacheManager;
import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;
import com.xuejiai.aaf.framework.intelligent.ai.speech.SpeechService;
import com.xuejiai.aaf.framework.intelligent.ai.speech.realtime.TtsListener;
import com.xuejiai.aaf.framework.intelligent.ai.speech.realtime.TtsTask;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRoutingContext;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class VoiceTtsStreamServiceTest extends BaseMockitoUnitTest {

    private static final Long USER_ID = 7L;
    private static final String TEXT = "你好，世界";
    private static final String VOICE = "longxiaochun_v3";
    private static final int MARKUP = 5;
    private static final long COST = 12L;

    @Mock private SpeechService speechService;
    @Mock private AiCreditGuard creditGuard;
    @Mock private ConfigCacheManager configCacheManager;
    @Mock private OperatorContext operatorContext;
    @Mock private TtsTask task;
    @InjectMocks private VoiceTtsStreamService service;

    private final AiModel model = new AiModel();

    @Test
    @DisplayName("Given 积分不足 When 流式合成 Then 抛出积分异常且不启动合成")
    void should_not_synthesize_when_precheck_fails() {
        // mock 方法
        stubEstimate();
        doThrow(new InsufficientCreditsException(USER_ID, 0L, COST, 0L))
                .when(creditGuard)
                .precheck(USER_ID, CapabilityRoutingContext.CAP_SPEECH_TTS, COST);

        // 调用 + 断言
        assertThatThrownBy(() -> service.synthesizeStream(TEXT, VOICE))
                .isInstanceOf(InsufficientCreditsException.class);
        verify(speechService, never()).startTtsTask(anyString(), any(), any());
        verify(creditGuard, never())
                .settleFixedReturningTxId(any(), anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("Given 未解析到登录用户 When 流式合成 Then 抛出未登录异常且不预检")
    void should_throw_unauthorized_when_owner_missing() {
        // mock 方法
        when(operatorContext.currentOwnerId()).thenReturn(Optional.empty());

        // 调用 + 断言
        assertThatThrownBy(() -> service.synthesizeStream(TEXT, VOICE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未登录");
        verify(creditGuard, never()).precheck(any(), anyString(), anyLong());
    }

    @Test
    @DisplayName("Given 预检通过 When 合成流正常完成 Then 按预估值结算并按序输出分片")
    void should_settle_when_stream_completes() {
        // 准备参数
        byte[] first = {1, 2};
        byte[] second = {3, 4, 5};

        // mock 方法
        stubEstimate();
        when(speechService.startTtsTask(eq(TEXT), eq(VOICE), any()))
                .thenAnswer(
                        invocation -> {
                            TtsListener listener = invocation.getArgument(2);
                            listener.onAudio(first);
                            listener.onAudio(second);
                            listener.onComplete();
                            return task;
                        });

        // 调用
        var chunks = service.synthesizeStream(TEXT, VOICE).collectList().block();

        // 断言
        assertThat(chunks).containsExactly(first, second);
        verify(creditGuard).precheck(USER_ID, CapabilityRoutingContext.CAP_SPEECH_TTS, COST);
        verify(creditGuard)
                .settleFixedReturningTxId(
                        USER_ID, COST, CapabilityRoutingContext.CAP_SPEECH_TTS, "对话语音合成");
    }

    @Test
    @DisplayName("Given 预检通过 When 合成流出错 Then 向下游传递错误且不结算")
    void should_not_settle_when_stream_fails() {
        // mock 方法
        stubEstimate();
        when(speechService.startTtsTask(eq(TEXT), eq(VOICE), any()))
                .thenAnswer(
                        invocation -> {
                            TtsListener listener = invocation.getArgument(2);
                            listener.onAudio(new byte[] {1});
                            listener.onError(new IllegalStateException("上游合成失败"));
                            return task;
                        });

        // 调用 + 断言
        var stream = service.synthesizeStream(TEXT, VOICE);
        assertThatThrownBy(stream::blockLast).hasMessageContaining("上游合成失败");
        verify(creditGuard, never())
                .settleFixedReturningTxId(any(), anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("Given 合成进行中 When 客户端取消订阅 Then 取消合成任务且不结算")
    void should_cancel_task_and_not_settle_when_downstream_cancels() {
        // 准备参数
        var listenerRef = new AtomicReference<TtsListener>();
        var received = new ArrayList<byte[]>();

        // mock 方法
        stubEstimate();
        when(speechService.startTtsTask(eq(TEXT), eq(VOICE), any()))
                .thenAnswer(
                        invocation -> {
                            listenerRef.set(invocation.getArgument(2));
                            return task;
                        });

        // 调用
        var subscription = service.synthesizeStream(TEXT, VOICE).subscribe(received::add);
        listenerRef.get().onAudio(new byte[] {9});
        subscription.dispose();
        listenerRef.get().onComplete();

        // 断言
        assertThat(received).hasSize(1);
        verify(task).cancel();
        verify(creditGuard, never())
                .settleFixedReturningTxId(any(), anyLong(), anyString(), anyString());
    }

    private void stubEstimate() {
        when(operatorContext.currentOwnerId()).thenReturn(Optional.of(USER_ID));
        when(configCacheManager.getAiModelByModelId(SpeechService.REALTIME_TTS_MODEL_ID))
                .thenReturn(model);
        when(creditGuard.getMarkupRate()).thenReturn(MARKUP);
        when(speechService.estimateCost(model, TEXT, MARKUP)).thenReturn(COST);
    }
}
