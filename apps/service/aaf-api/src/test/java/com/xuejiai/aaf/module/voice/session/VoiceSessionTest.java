package com.xuejiai.aaf.module.voice.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.framework.intelligent.ai.speech.realtime.SpeechPoolExhaustedException;
import com.xuejiai.aaf.module.voice.config.VoiceSessionProperties;

class VoiceSessionTest {

    private static final String EXIT_PHRASES = ",\"exitPhrases\":[\"退出对话\",\"拜拜\"]";

    // ============================== 握手 ==============================

    @Test
    @DisplayName("Given 合法 hello When 握手 Then 回 ready 并声明 PCM 下行、可推测、客户端断句")
    void should_reply_ready_when_hello_valid() {
        // 准备参数
        var h = new VoiceSessionHarness();

        // 调用
        h.hello("");

        // 断言
        var ready = h.only("ready");
        assertThat(ready.get("proto")).isEqualTo(3);
        assertThat(ready.get("endpointing")).isEqualTo("client");
        assertThat(ready.get("caps"))
                .isEqualTo(Map.of("speculative", true, "reply", true, "tts", true));
        assertThat(ready.get("audioOut")).isEqualTo(Map.of("codec", "pcm_s16le", "rate", 16000));
    }

    @Test
    @DisplayName("Given proto 不是 3 When hello Then 回 error 并关闭连接且不回 ready")
    void should_reject_when_proto_unsupported() {
        // 准备参数
        var h = new VoiceSessionHarness();

        // 调用
        h.send("{\"type\":\"hello\",\"proto\":2}");

        // 断言
        assertThat(h.messages("ready")).isEmpty();
        assertThat(h.only("error")).containsEntry("fallback", false);
        assertThat(h.channel.closeCode).isEqualTo(4400);
    }

    @Test
    @DisplayName("Given 未发 hello When 超时 Then 以 4408 关闭连接")
    void should_close_when_hello_times_out() {
        // 准备参数
        var h = new VoiceSessionHarness();

        // 调用
        h.timer.fire(5000);

        // 断言
        assertThat(h.channel.closeCode).isEqualTo(4408);
    }

    @Test
    @DisplayName("Given 未握手 When 发 turn_start Then 忽略且不借出上游连接")
    void should_ignore_turn_when_not_hello() {
        // 准备参数
        var h = new VoiceSessionHarness();

        // 调用
        h.turnStart(1, "auto");

        // 断言
        assertThat(h.speech.asrTasks).isEmpty();
        assertThat(h.channel.texts).isEmpty();
    }

    @Test
    @DisplayName("Given 未知 type When 收到 Then 忽略且不关闭连接")
    void should_ignore_unknown_type() {
        // 准备参数
        var h = new VoiceSessionHarness();
        h.hello("");

        // 调用
        h.send("{\"type\":\"from_the_future\"}");

        // 断言
        assertThat(h.channel.closeCode).isEqualTo(-1);
        assertThat(h.types()).containsExactly("ready");
    }

    // ============================== 完整话轮（非推测）==============================

    @Test
    @DisplayName("Given 客户端断句 When 说话到 commit 再到回复播完 Then 依序产出 asr_final→回复增量→句子语音→turn_end 并按话轮结算")
    void should_run_full_turn_when_commit() {
        // 准备参数
        var h = new VoiceSessionHarness();
        h.hello("");
        h.turnStart(1, "auto");
        var asr = h.speech.lastAsr();
        asr.tailSentence = "今天天气怎么样";

        // 调用：说话
        h.mic(1, 3200);
        asr.listener.onPartial("今天");
        h.clock.addAndGet(500);
        h.send("{\"type\":\"commit\",\"turn\":1,\"rev\":0}");

        // 断言：识别定稿并启动大脑
        assertThat(asr.sent).hasSize(1);
        assertThat(asr.finished).isTrue();
        var asrFinal = h.only("asr_final");
        assertThat(asrFinal).containsEntry("text", "今天天气怎么样").containsEntry("reply", true);
        assertThat(h.client.replyRequests).containsExactly("今天天气怎么样");

        // 调用：大脑流式回复两句
        h.client.replySink.tryEmitNext("今天晴天，");
        h.client.replySink.tryEmitNext("很适合出门。记得带伞");
        h.client.replySink.tryEmitComplete();

        // 断言：句子被宣告并进入合成
        var sentences = h.messages("tts_sentence");
        assertThat(sentences).extracting(m -> m.get("text")).containsExactly("今天晴天，很适合出门。", "记得带伞");
        assertThat(h.speech.ttsTasks).hasSize(2);

        // 调用：合成完成
        h.speech.ttsTasks.get(0).audio(100);
        h.speech.ttsTasks.get(0).complete();
        h.speech.ttsTasks.get(1).audio(50);
        h.speech.ttsTasks.get(1).complete();

        // 断言
        assertThat(h.only("reply_done")).containsEntry("sentences", 2);
        assertThat(h.only("reply_done").get("text")).isEqualTo("今天晴天，很适合出门。记得带伞");
        assertThat(h.types()).endsWith("turn_end");
        assertThat(h.channel.binaries).hasSize(2);
        assertThat(h.client.asrSettlements).containsExactly(2000);
        assertThat(h.client.ttsSettlements).containsExactly("今天晴天，很适合出门。记得带伞");
    }

    @Test
    @DisplayName("Given 第二句先合成完 When 第一句尚未结束 Then 第二句音频先缓冲，按 sid 顺序放行并按序发 tts_sentence_end")
    void should_deliver_sentence_audio_in_order() {
        // 准备参数
        var h = new VoiceSessionHarness();
        h.hello("");
        h.turnStart(1, "auto");
        h.speech.lastAsr().tailSentence = "你好吗";
        h.send("{\"type\":\"commit\",\"turn\":1,\"rev\":0}");
        h.client.replySink.tryEmitNext("第一句话说完了。第二句话也说完了。");
        h.client.replySink.tryEmitComplete();
        var first = h.speech.ttsTasks.get(0);
        var second = h.speech.ttsTasks.get(1);

        // 调用：第二句先出音频并完成
        second.audio(20);
        second.complete();

        // 断言：此刻没有任何音频帧或句末事件下发
        assertThat(h.channel.binaries).isEmpty();
        assertThat(h.messages("tts_sentence_end")).isEmpty();

        // 调用：第一句出音频并完成
        first.audio(10);
        first.complete();

        // 断言
        assertThat(h.channel.binaries)
                .extracting(frame -> frame.length - 5)
                .containsExactly(10, 20);
        var sids = h.messages("tts_sentence").stream().map(m -> m.get("sid")).toList();
        assertThat(h.messages("tts_sentence_end")).extracting(m -> m.get("sid")).isEqualTo(sids);
        assertThat(h.types()).endsWith("turn_end");
    }

    @Test
    @DisplayName("Given 并发上限为 2 When 回复有 3 句 Then 第 3 句等前面有句子结束后才借出合成连接")
    void should_limit_tts_in_flight() {
        // 准备参数
        var h = new VoiceSessionHarness();
        h.hello("");
        h.turnStart(1, "auto");
        h.speech.lastAsr().tailSentence = "你好吗";
        h.send("{\"type\":\"commit\",\"turn\":1,\"rev\":0}");

        // 调用
        h.client.replySink.tryEmitNext("第一句话说完了。第二句话说完了。第三句话说完了。");

        // 断言
        assertThat(h.speech.ttsTasks).hasSize(2);

        // 调用
        h.speech.ttsTasks.get(0).complete();

        // 断言
        assertThat(h.speech.ttsTasks).hasSize(3);
    }

    @Test
    @DisplayName("Given reply 为 none When commit Then 只做识别：asr_final.reply=false、不调用大脑、直接 turn_end")
    void should_not_call_brain_when_reply_none() {
        // 准备参数
        var h = new VoiceSessionHarness();
        h.hello("");
        h.turnStart(1, "none");
        h.speech.lastAsr().tailSentence = "记个笔记";

        // 调用
        h.send("{\"type\":\"commit\",\"turn\":1,\"rev\":0}");

        // 断言
        assertThat(h.only("asr_final")).containsEntry("reply", false);
        assertThat(h.client.replyRequests).isEmpty();
        assertThat(h.types()).endsWith("turn_end");
    }

    @Test
    @DisplayName("Given 转写命中退出口令 When 定稿 Then asr_final.exit=true 且不调用大脑")
    void should_flag_exit_phrase_and_skip_brain() {
        // 准备参数
        var h = new VoiceSessionHarness();
        h.hello(EXIT_PHRASES);
        h.turnStart(1, "auto");
        h.speech.lastAsr().tailSentence = "退出，对话！";

        // 调用
        h.send("{\"type\":\"commit\",\"turn\":1,\"rev\":0}");

        // 断言
        assertThat(h.only("asr_final")).containsEntry("exit", true).containsEntry("reply", false);
        assertThat(h.client.replyRequests).isEmpty();
    }

    // ============================== 推测与续说 ==============================

    @Test
    @DisplayName("Given 软结束后用户续说 When speech_resume Then 撤回推测、在新的上游任务上续识别、重放暂存音频并拼接前文")
    void should_discard_speculation_and_continue_on_new_task_when_resumed() {
        // 准备参数
        var h = new VoiceSessionHarness();
        h.hello("");
        h.turnStart(1, "auto");
        var first = h.speech.lastAsr();
        first.tailSentence = "帮我订一张";
        h.mic(1, 3200);

        // 调用：软结束 → 推测性定稿并启动大脑
        h.send("{\"type\":\"speech_end\",\"turn\":1,\"rev\":0}");

        // 断言
        assertThat(first.finished).isTrue();
        assertThat(h.messages("asr_final")).hasSize(1);
        assertThat(h.messages("asr_final").get(0)).containsEntry("rev", 0);
        assertThat(h.client.replyRequests).containsExactly("帮我订一张");

        // 调用：软结束窗口内仍有音频到达，然后用户续说
        h.mic(1, 1600);
        h.send("{\"type\":\"speech_resume\",\"turn\":1,\"rev\":1}");

        // 断言：旧推测被撤回，新任务接上并重放暂存音频
        assertThat(h.client.replyCancelledByDownstream).isTrue();
        assertThat(h.speech.asrTasks).hasSize(2);
        var second = h.speech.lastAsr();
        assertThat(second.sent).hasSize(1);
        assertThat(second.sent.get(0)).hasSize(1600);

        // 调用：续说结束并提交
        second.tailSentence = "去北京的机票";
        h.send("{\"type\":\"speech_end\",\"turn\":1,\"rev\":1}");
        h.send("{\"type\":\"commit\",\"turn\":1,\"rev\":1}");

        // 断言：rev=1 的定稿包含前后两段，并以完整文本重新回复
        var finals = h.messages("asr_final");
        assertThat(finals).hasSize(2);
        assertThat(finals.get(1)).containsEntry("rev", 1).containsEntry("text", "帮我订一张去北京的机票");
        assertThat(h.client.replyRequests).containsExactly("帮我订一张", "帮我订一张去北京的机票");
    }

    @Test
    @DisplayName("Given 服务端不开推测 When 收到 speech_end Then 忽略，等 commit 才定稿")
    void should_ignore_speech_end_when_not_speculative() {
        // 准备参数
        var props =
                new VoiceSessionProperties(
                        600, 5000, 3, 2, 4, 60, false, true, 700, 64_000, 16_384, 100, 3000,
                        Map.of());
        var h = new VoiceSessionHarness(props);
        h.hello("");
        h.turnStart(1, "auto");

        // 调用
        h.send("{\"type\":\"speech_end\",\"turn\":1,\"rev\":0}");

        // 断言
        assertThat(h.only("ready").get("caps"))
                .isEqualTo(Map.of("speculative", false, "reply", true, "tts", true));
        assertThat(h.speech.lastAsr().finished).isFalse();
        assertThat(h.messages("asr_final")).isEmpty();
    }

    // ============================== 取消 ==============================

    @Test
    @DisplayName("Given 回复进行中 When cancel(superseded) Then 停止识别/大脑/合成，通知客户端听到的句数，commit 过的识别照常结算")
    void should_stop_everything_and_report_heard_when_cancelled() {
        // 准备参数
        var h = new VoiceSessionHarness();
        h.hello("");
        h.turnStart(1, "auto");
        h.speech.lastAsr().tailSentence = "讲个故事";
        h.send("{\"type\":\"commit\",\"turn\":1,\"rev\":0}");
        h.client.replySink.tryEmitNext("从前有座山。山里有座庙。");
        var tts = h.speech.ttsTasks;

        // 调用
        h.send(
                "{\"type\":\"cancel\",\"turn\":1,\"reason\":\"superseded\",\"heard\":{\"sentences\":1}}");

        // 断言
        assertThat(h.client.replyCancelledByDownstream).isTrue();
        assertThat(tts).allMatch(task -> task.cancelled);
        assertThat(h.client.cancellations).containsExactly("superseded:1:从前有座山。山里有座庙。");
        assertThat(h.client.asrSettlements).containsExactly(2000);

        // 调用：取消后迟到的上游事件不再产生输出
        var before = h.channel.texts.size();
        tts.get(0).audio(10);
        tts.get(0).complete();
        h.client.replySink.tryEmitNext("不该出现的增量");

        // 断言
        assertThat(h.channel.texts).hasSize(before);
        assertThat(h.channel.binaries).isEmpty();
    }

    @Test
    @DisplayName("Given 设备没有采纳回复 When cancel(not_adopted) Then 不收语音合成费且通知客户端不写入历史")
    void should_not_bill_tts_when_not_adopted() {
        // 准备参数
        var h = new VoiceSessionHarness();
        h.hello("");
        h.turnStart(1, "auto");
        h.speech.lastAsr().tailSentence = "你好";
        h.send("{\"type\":\"commit\",\"turn\":1,\"rev\":0}");
        h.client.replySink.tryEmitNext("你好呀，很高兴见到你。");
        h.speech.ttsTasks.get(0).complete();

        // 调用
        h.send("{\"type\":\"cancel\",\"turn\":1,\"reason\":\"not_adopted\"}");

        // 断言
        assertThat(h.client.ttsSettlements).isEmpty();
        assertThat(h.client.cancellations).hasSize(1);
        assertThat(h.client.cancellations.get(0)).startsWith("not_adopted:");
    }

    @Test
    @DisplayName("Given 未知或已结束的 turn When cancel Then 幂等忽略")
    void should_ignore_cancel_for_unknown_turn() {
        // 准备参数
        var h = new VoiceSessionHarness();
        h.hello("");

        // 调用
        h.send("{\"type\":\"cancel\",\"turn\":99,\"reason\":\"client_cancel\"}");

        // 断言
        assertThat(h.types()).containsExactly("ready");
    }

    @Test
    @DisplayName("Given 未 commit 的话轮 When cancel Then 不结算（废弃的推测成本由平台承担）")
    void should_not_settle_when_cancelled_before_commit() {
        // 准备参数
        var h = new VoiceSessionHarness();
        h.hello("");
        h.turnStart(1, "auto");
        h.mic(1, 3200);

        // 调用
        h.send("{\"type\":\"cancel\",\"turn\":1,\"reason\":\"client_cancel\"}");

        // 断言
        assertThat(h.speech.lastAsr().aborted).isTrue();
        assertThat(h.client.asrSettlements).isEmpty();
    }

    // ============================== 错误与池 ==============================

    @Test
    @DisplayName("Given 识别对象池耗尽 When turn_start Then 回 11000019 且允许回退，不留下话轮")
    void should_fail_fast_when_pool_exhausted() {
        // 准备参数
        var h = new VoiceSessionHarness();
        h.hello("");
        h.speech.asrOpenFailure = new SpeechPoolExhaustedException("full", null);

        // 调用
        h.turnStart(1, "auto");

        // 断言
        assertThat(h.only("error"))
                .containsEntry("turn", 1)
                .containsEntry("code", 11_000_019)
                .containsEntry("fallback", true);
        h.speech.asrOpenFailure = null;
        h.turnStart(1, "auto");
        assertThat(h.speech.asrTasks).hasSize(1);
    }

    @Test
    @DisplayName("Given 积分预检失败 When turn_start Then 透传业务错误码 11000018 并允许回退")
    void should_pass_through_business_error_code_when_precheck_fails() {
        // 准备参数
        var h = new VoiceSessionHarness();
        h.hello("");
        h.client.precheckFailure = new BusinessException(11_000_018, "设备所有者积分不足");

        // 调用
        h.turnStart(1, "auto");

        // 断言
        assertThat(h.only("error"))
                .containsEntry("code", 11_000_018)
                .containsEntry("fallback", true);
        assertThat(h.speech.asrTasks).isEmpty();
    }

    @Test
    @DisplayName("Given 大脑在产出任何文字前失败 When 回复 Then 回 error 允许回退且不发 turn_end")
    void should_signal_fallback_when_brain_fails_before_any_text() {
        // 准备参数
        var h = new VoiceSessionHarness();
        h.hello("");
        h.turnStart(1, "auto");
        h.speech.lastAsr().tailSentence = "你好";
        h.send("{\"type\":\"commit\",\"turn\":1,\"rev\":0}");

        // 调用
        h.client.replySink.tryEmitError(new IllegalStateException("model down"));

        // 断言
        assertThat(h.only("error")).containsEntry("fallback", true);
        assertThat(h.messages("turn_end")).isEmpty();
    }

    @Test
    @DisplayName("Given 某句合成失败 When 其它句正常 Then 该句 tts_sentence_end 带 error，其余照常完成")
    void should_report_sentence_error_and_continue() {
        // 准备参数
        var h = new VoiceSessionHarness();
        h.hello("");
        h.turnStart(1, "auto");
        h.speech.lastAsr().tailSentence = "你好";
        h.send("{\"type\":\"commit\",\"turn\":1,\"rev\":0}");
        h.client.replySink.tryEmitNext("第一句话说完了。第二句话说完了。");
        h.client.replySink.tryEmitComplete();

        // 调用
        h.speech.ttsTasks.get(0).listener.onError(new IllegalStateException("tts down"));
        h.speech.ttsTasks.get(1).audio(10);
        h.speech.ttsTasks.get(1).complete();

        // 断言
        var ends = h.messages("tts_sentence_end");
        assertThat(ends).hasSize(2);
        assertThat(ends.get(0)).containsKey("error");
        assertThat(ends.get(1)).doesNotContainKey("error");
        assertThat(h.types()).endsWith("turn_end");
    }

    // ============================== 服务端断句（网页）==============================

    @Test
    @DisplayName("Given 服务端断句 When 一句定稿后静默超过阈值 Then 发 endpoint 并结束识别、启动回复")
    void should_endpoint_on_server_when_silence_after_sentence() {
        // 准备参数
        var h = new VoiceSessionHarness();
        h.hello(",\"endpointing\":\"server\"");
        h.turnStart(1, "auto");
        var asr = h.speech.lastAsr();

        // 调用
        asr.listener.onSentenceEnd("今天几号");
        h.timer.fire(700);

        // 断言
        assertThat(h.only("ready")).containsEntry("endpointing", "server");
        assertThat(h.only("endpoint")).containsEntry("turn", 1);
        assertThat(asr.finished).isTrue();
        assertThat(h.only("asr_final")).containsEntry("text", "今天几号");
        assertThat(h.client.replyRequests).containsExactly("今天几号");
    }

    @Test
    @DisplayName("Given 服务端断句 When 静默期间又有新文本 Then 不触发 endpoint")
    void should_not_endpoint_when_new_speech_arrives() {
        // 准备参数
        var h = new VoiceSessionHarness();
        h.hello(",\"endpointing\":\"server\"");
        h.turnStart(1, "auto");
        var asr = h.speech.lastAsr();
        asr.listener.onSentenceEnd("今天几号");

        // 调用
        asr.listener.onPartial("还有");
        h.timer.fire(700);

        // 断言
        assertThat(h.messages("endpoint")).isEmpty();
        assertThat(asr.finished).isFalse();
    }

    // ============================== 独立 tts 请求 ==============================

    @Test
    @DisplayName(
            "Given tts 请求 When 合成 Then 以 turn=0 带 req 应答 tts_sentence、音频与 tts_sentence_end，并结算")
    void should_serve_standalone_tts_request() {
        // 准备参数
        var h = new VoiceSessionHarness();
        h.hello("");

        // 调用
        h.send("{\"type\":\"tts\",\"req\":7,\"text\":\"提醒你该吃药了\"}");
        h.speech.ttsTasks.get(0).audio(40);
        h.speech.ttsTasks.get(0).complete();

        // 断言
        assertThat(h.only("tts_sentence")).containsEntry("turn", 0).containsEntry("req", 7);
        assertThat(h.channel.binaries).hasSize(1);
        assertThat(h.messages("tts_sentence_end")).hasSize(1);
        assertThat(h.client.ttsSettlements).containsExactly("提醒你该吃药了");
    }

    // ============================== 连接生命周期 ==============================

    @Test
    @DisplayName("Given 进行中的话轮 When 连接关闭 Then 取消上游任务并按已发生的用量结算、通知客户端清理")
    void should_cancel_and_settle_when_connection_closed() {
        // 准备参数
        var h = new VoiceSessionHarness();
        h.hello("");
        h.turnStart(1, "auto");
        h.speech.lastAsr().tailSentence = "你好";
        h.send("{\"type\":\"commit\",\"turn\":1,\"rev\":0}");
        h.client.replySink.tryEmitNext("你好呀，很高兴见到你。");

        // 调用
        h.session.onClosed();

        // 断言
        assertThat(h.speech.ttsTasks).allMatch(task -> task.cancelled);
        assertThat(h.client.replyCancelledByDownstream).isTrue();
        assertThat(h.client.asrSettlements).containsExactly(2000);
        assertThat(h.client.closed).isTrue();
    }

    @Test
    @DisplayName("Given 无话轮且空闲超时 When 巡检 Then 发 session_close(idle_timeout) 并关闭")
    void should_close_when_idle() {
        // 准备参数
        var h = new VoiceSessionHarness();
        h.hello("");

        // 调用
        h.clock.addAndGet(601_000);
        h.session.checkIdle();

        // 断言
        assertThat(h.only("session_close")).containsEntry("reason", "idle_timeout");
        assertThat(h.channel.closeCode).isEqualTo(1000);
    }

    @Test
    @DisplayName("Given 有进行中的话轮 When 巡检 Then 不会因空闲被关闭")
    void should_not_close_when_turn_active() {
        // 准备参数
        var h = new VoiceSessionHarness();
        h.hello("");
        h.turnStart(1, "auto");

        // 调用
        h.clock.addAndGet(601_000);
        h.session.checkIdle();

        // 断言
        assertThat(h.messages("session_close")).isEmpty();
    }

    @Test
    @DisplayName("Given 同一话轮号重复 turn_start When 收到 Then 忽略，不重复借出上游连接")
    void should_ignore_duplicate_turn_start() {
        // 准备参数
        var h = new VoiceSessionHarness();
        h.hello("");
        h.turnStart(1, "auto");

        // 调用
        h.turnStart(1, "auto");

        // 断言
        assertThat(h.speech.asrTasks).hasSize(1);
    }

    @Test
    @DisplayName("Given 话轮数达到上限 When 再开新话轮 Then 回 11000019 并允许回退")
    void should_reject_when_too_many_active_turns() {
        // 准备参数
        var h = new VoiceSessionHarness();
        h.hello("");
        h.turnStart(1, "auto");
        h.turnStart(2, "auto");
        h.turnStart(3, "auto");

        // 调用
        h.turnStart(4, "auto");

        // 断言
        assertThat(h.only("error")).containsEntry("turn", 4).containsEntry("fallback", true);
        assertThat(h.speech.asrTasks).hasSize(3);
    }
}
