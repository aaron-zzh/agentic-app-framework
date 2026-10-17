package com.xuejiai.aaf.module.voice.session;

import static com.xuejiai.aaf.module.voice.protocol.VoiceProtocol.AUDIO_RATE;
import static com.xuejiai.aaf.module.voice.protocol.VoiceProtocol.CODEC_PCM;
import static com.xuejiai.aaf.module.voice.protocol.VoiceProtocol.ENDPOINT_CLIENT;
import static com.xuejiai.aaf.module.voice.protocol.VoiceProtocol.ENDPOINT_SERVER;
import static com.xuejiai.aaf.module.voice.protocol.VoiceProtocol.ERR_BAD_REQUEST;
import static com.xuejiai.aaf.module.voice.protocol.VoiceProtocol.ERR_GATEWAY_FAILED;
import static com.xuejiai.aaf.module.voice.protocol.VoiceProtocol.FRAME_MIC;
import static com.xuejiai.aaf.module.voice.protocol.VoiceProtocol.FRAME_TTS;
import static com.xuejiai.aaf.module.voice.protocol.VoiceProtocol.PROTO;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.intelligent.ai.speech.SpeechService;
import com.xuejiai.aaf.framework.intelligent.ai.speech.realtime.AsrListener;
import com.xuejiai.aaf.framework.intelligent.ai.speech.realtime.SpeechPoolExhaustedException;
import com.xuejiai.aaf.framework.intelligent.ai.speech.realtime.TtsListener;
import com.xuejiai.aaf.module.voice.api.VoiceClient;
import com.xuejiai.aaf.module.voice.config.VoiceSessionProperties;
import com.xuejiai.aaf.module.voice.protocol.InboundMessage;
import com.xuejiai.aaf.module.voice.protocol.VoiceFrames;
import com.xuejiai.aaf.module.voice.session.VoiceTurn.SentenceJob;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * 一条语音会话（协议 v3）的服务端状态机，设备端与网页端共用。
 *
 * <p>线程模型：所有入站事件、上游回调、定时器与回复流都先经 {@code actor}（{@link SerialExecutor}）串行化再改状态，因此本类内部没有锁。
 * 会阻塞的上游调用（结束识别任务、调用大脑、结算）放到 {@code deps.blocking()}（虚拟线程）执行，结果再回到 actor。
 *
 * <p>话轮生命周期：{@code turn_start} 借出识别连接 → 音频 → {@code speech_end}(推测) / {@code commit} 或服务端断句 →
 * {@code asr_final} → 大脑回复增量 → 切句 → 句子级 TTS 流水线 → {@code reply_done} → {@code turn_end}。{@code
 * speech_resume} 撤回推测并在新的上游任务上续识别；{@code cancel} 立即停止并归还/作废上游连接。
 */
@Slf4j
public final class VoiceSession {

    /** 传输通道抽象，隔离 Spring WebSocket，便于测试。 */
    public interface Channel {

        void sendText(String json);

        void sendBinary(byte[] frame);

        void close(int code, String reason);
    }

    /** 延时调度，返回取消句柄。回调线程不保证，使用方需自行切回 actor。 */
    public interface Timer {

        Runnable schedule(long delayMs, Runnable task);
    }

    /** 会话运行依赖。 */
    public record Deps(
            SpeechService speech,
            VoiceSessionProperties props,
            Executor blocking,
            Timer timer,
            LongSupplier clock) {}

    private static final int CLOSE_HELLO_TIMEOUT = 4408;
    private static final int CLOSE_BAD_REQUEST = 4400;

    private final String id;
    private final VoiceClient client;
    private final Channel channel;
    private final SerialExecutor actor;
    private final Deps deps;
    private final VoiceSessionProperties props;
    private final SpeechService speech;

    private final Map<Integer, VoiceTurn> turns = new LinkedHashMap<>();
    private final Map<Integer, SentenceJob> ttsRequests = new HashMap<>();

    private boolean helloDone;
    private boolean closed;
    private boolean speculative;
    private String endpointing = ENDPOINT_CLIENT;
    private String lang = "zh-CN";
    private String voice;
    private Set<String> exitPhrases = Set.of();
    private int sidSeq;
    private long lastActivityMs;
    private Runnable helloTimer = () -> {};

    public VoiceSession(
            String id, VoiceClient client, Channel channel, SerialExecutor actor, Deps deps) {
        this.id = id;
        this.client = client;
        this.channel = channel;
        this.actor = actor;
        this.deps = deps;
        this.props = deps.props();
        this.speech = deps.speech();
    }

    public String clientKey() {
        return client.clientKey();
    }

    // ============================== 入口（线程安全）==============================

    /** 连接建立：开始等待 hello。 */
    public void onOpen() {
        actor.execute(
                () -> {
                    lastActivityMs = now();
                    helloTimer =
                            deps.timer()
                                    .schedule(
                                            props.helloTimeoutMs(),
                                            () -> actor.execute(this::onHelloTimeout));
                });
    }

    public void onText(String json) {
        actor.execute(
                () -> {
                    touch();
                    var message = InboundMessage.parse(json);
                    if (message != null) {
                        dispatch(message);
                    }
                });
    }

    public void onBinary(ByteBuffer payload) {
        var frame = VoiceFrames.decode(payload);
        if (frame != null) {
            actor.execute(() -> onMic(frame));
        }
    }

    /** 连接已关闭（对端关闭或传输错误）：取消全部进行中的工作并结算。 */
    public void onClosed() {
        actor.execute(this::shutdown);
    }

    /** 服务端主动关闭会话，如被新连接顶替或服务关闭。 */
    public void closeBy(String reason) {
        actor.execute(
                () -> {
                    if (!closed) {
                        send("session_close", "reason", reason);
                        channel.close(1000, reason);
                    }
                });
    }

    /** 周期性空闲检查，由处理器的定时器调用。 */
    public void checkIdle() {
        actor.execute(
                () -> {
                    if (closed || !helloDone || hasActiveWork()) {
                        return;
                    }
                    if (now() - lastActivityMs >= props.idleTimeoutSec() * 1000L) {
                        send("session_close", "reason", "idle_timeout");
                        channel.close(1000, "idle_timeout");
                    }
                });
    }

    // ============================== 分发 ==============================

    private void dispatch(InboundMessage message) {
        if (!helloDone) {
            if ("hello".equals(message.type())) {
                onHello(message);
            }
            return;
        }
        switch (message.type()) {
            case "turn_start" -> onTurnStart(message);
            case "speech_end" -> onSpeechEnd(message);
            case "speech_resume" -> onSpeechResume(message);
            case "commit" -> onCommit(message);
            case "cancel" -> onCancel(message);
            case "tts" -> onTtsRequest(message);
            default -> {
                // 未知 type 一律忽略，不关连接
            }
        }
    }

    private void onHelloTimeout() {
        if (!helloDone && !closed) {
            channel.close(CLOSE_HELLO_TIMEOUT, "hello timeout");
        }
    }

    private void onHello(InboundMessage hello) {
        if (hello.intOf("proto", 0) != PROTO) {
            reject("不支持的协议版本，需要 proto=3");
            return;
        }
        var audioIn = hello.mapOf("audioIn");
        var codec = audioIn.get("codec");
        var rate = audioIn.get("rate");
        if ((codec != null && !CODEC_PCM.equals(codec))
                || (rate instanceof Number number && number.intValue() != AUDIO_RATE)) {
            reject("上行音频必须是 16kHz pcm_s16le");
            return;
        }
        var codecs = hello.mapOf("audioOut").get("codecs");
        if (codecs instanceof List<?> list && !list.isEmpty() && !list.contains(CODEC_PCM)) {
            reject("客户端不支持 pcm_s16le 下行音频");
            return;
        }

        var wantsServer = ENDPOINT_SERVER.equals(hello.strOf("endpointing", ENDPOINT_CLIENT));
        endpointing = wantsServer && props.serverEndpointing() ? ENDPOINT_SERVER : ENDPOINT_CLIENT;
        speculative = props.speculative() && ENDPOINT_CLIENT.equals(endpointing);
        lang = hello.strOf("lang", lang);
        voice = props.voices().get(hello.strOf("voice", ""));
        var phrases = new HashSet<String>();
        hello.stringsOf("exitPhrases").forEach(phrase -> phrases.add(normalize(phrase)));
        phrases.remove("");
        exitPhrases = Set.copyOf(phrases);

        helloDone = true;
        helloTimer.run();
        var caps = new LinkedHashMap<String, Object>();
        caps.put("speculative", speculative);
        caps.put("reply", true);
        caps.put("tts", true);
        var audioOut = new LinkedHashMap<String, Object>();
        audioOut.put("codec", CODEC_PCM);
        audioOut.put("rate", SpeechService.REALTIME_TTS_SAMPLE_RATE);
        send(
                "ready",
                "proto",
                PROTO,
                "caps",
                caps,
                "endpointing",
                endpointing,
                "audioOut",
                audioOut,
                "idleTimeoutSec",
                props.idleTimeoutSec(),
                "session",
                id);
        log.info(
                "voice_session ready client={} session={} endpointing={} speculative={}",
                client.clientKey(),
                id,
                endpointing,
                speculative);
    }

    private void reject(String message) {
        sendError(null, null, ERR_BAD_REQUEST, message, false);
        channel.close(CLOSE_BAD_REQUEST, message);
    }

    // ============================== 话轮：开始与音频 ==============================

    private void onTurnStart(InboundMessage message) {
        var turnId = message.intOf("turn", -1);
        if (turnId < 0) {
            sendError(null, null, ERR_BAD_REQUEST, "turn_start 缺少 turn", false);
            return;
        }
        if (turns.containsKey(turnId)) {
            return;
        }
        if (activeTurns() >= props.maxActiveTurns()) {
            sendError(turnId, null, ERR_GATEWAY_FAILED, "并发话轮过多", true);
            return;
        }
        try {
            client.precheck();
        } catch (RuntimeException exception) {
            sendError(turnId, null, codeOf(exception), messageOf(exception), true);
            return;
        }
        var turn =
                new VoiceTurn(
                        turnId,
                        message.strOf("lang", lang),
                        "auto".equals(message.strOf("reply", "none")),
                        message.boolOf("tts", true),
                        now());
        turns.put(turnId, turn);
        openAsr(turn);
    }

    /** 借出识别连接并开始任务；失败时通知客户端并清理话轮。 */
    private boolean openAsr(VoiceTurn turn) {
        var seq = ++turn.asrSeq;
        try {
            turn.asr =
                    speech.openAsrTask(
                            turn.lang,
                            new AsrListener() {
                                @Override
                                public void onPartial(String text) {
                                    actor.execute(() -> onAsrPartial(turn, seq, text));
                                }

                                @Override
                                public void onSentenceEnd(String text) {
                                    actor.execute(() -> onAsrSentence(turn, seq, text));
                                }

                                @Override
                                public void onError(Throwable error) {
                                    actor.execute(() -> onAsrError(turn, seq, error));
                                }
                            });
            return true;
        } catch (RuntimeException exception) {
            log.warn("voice 识别任务开启失败 client={} turn={}", client.clientKey(), turn.id, exception);
            sendError(turn.id, null, codeOf(exception), messageOf(exception), true);
            turn.cancelled = true;
            turns.remove(turn.id);
            return false;
        }
    }

    private void onMic(VoiceFrames.Frame frame) {
        if (!helloDone || frame.type() != FRAME_MIC) {
            return;
        }
        touch();
        var turn = turns.get(frame.id());
        if (turn == null || turn.cancelled || turn.committed) {
            return;
        }
        turn.audioBytes += frame.payload().length;
        if (turn.softEnded || turn.finishing) {
            hold(turn, frame.payload());
        } else if (turn.asr != null) {
            turn.asr.sendAudio(frame.payload());
        }
    }

    /** 软结束后暂存续说音频，超出上限丢弃最旧的。 */
    private void hold(VoiceTurn turn, byte[] pcm) {
        turn.held.addLast(pcm);
        turn.heldBytes += pcm.length;
        while (turn.heldBytes > props.heldAudioMaxBytes() && turn.held.size() > 1) {
            turn.heldBytes -= turn.held.pollFirst().length;
        }
    }

    // ============================== 话轮：识别结果 ==============================

    private void onAsrPartial(VoiceTurn turn, int seq, String text) {
        if (turn.cancelled || seq != turn.asrSeq) {
            return;
        }
        turn.partial = text;
        turn.partialDirty = true;
        turn.speechVersion++;
        if (turn.firstPartialAtMs == 0) {
            turn.firstPartialAtMs = now();
        }
        emitPartial(turn, false);
    }

    private void onAsrSentence(VoiceTurn turn, int seq, String text) {
        if (turn.cancelled || text == null || text.isBlank()) {
            return;
        }
        turn.sentences.add(text);
        if (seq == turn.asrSeq) {
            turn.partial = "";
        }
        turn.partialDirty = true;
        turn.speechVersion++;
        emitPartial(turn, true);
        if (ENDPOINT_SERVER.equals(endpointing) && !turn.committed && !turn.finishing) {
            var version = turn.speechVersion;
            deps.timer()
                    .schedule(
                            props.serverEndpointSilenceMs(),
                            () -> actor.execute(() -> onEndpointTimer(turn, version)));
        }
    }

    private void onAsrError(VoiceTurn turn, int seq, Throwable error) {
        if (turn.cancelled || seq != turn.asrSeq) {
            return;
        }
        log.warn("voice 上游识别失败 client={} turn={}", client.clientKey(), turn.id, error);
        sendError(turn.id, null, ERR_GATEWAY_FAILED, "语音识别失败", true);
        cancelTurn(turn, "asr_error", 0, false);
    }

    private void emitPartial(VoiceTurn turn, boolean force) {
        var remaining = turn.lastPartialAtMs + props.partialMinIntervalMs() - now();
        if (force || remaining <= 0) {
            sendPartial(turn);
        } else if (!turn.partialFlushScheduled) {
            turn.partialFlushScheduled = true;
            deps.timer()
                    .schedule(
                            remaining,
                            () ->
                                    actor.execute(
                                            () -> {
                                                turn.partialFlushScheduled = false;
                                                if (!turn.cancelled && turn.partialDirty) {
                                                    sendPartial(turn);
                                                }
                                            }));
        }
    }

    private void sendPartial(VoiceTurn turn) {
        turn.lastPartialAtMs = now();
        turn.partialDirty = false;
        var text = turn.fullText();
        if (text.isBlank()) {
            return;
        }
        var stable = turn.stableText();
        if (stable.isBlank()) {
            send("asr_partial", "turn", turn.id, "rev", turn.rev, "text", text);
        } else {
            send("asr_partial", "turn", turn.id, "rev", turn.rev, "text", text, "stable", stable);
        }
    }

    /** 服务端断句：一句定稿后静默超过阈值且期间没有新文本，判定话轮结束。 */
    private void onEndpointTimer(VoiceTurn turn, int version) {
        if (turn.cancelled
                || turn.committed
                || turn.finishing
                || version != turn.speechVersion
                || turn.stableText().isBlank()) {
            return;
        }
        turn.committed = true;
        send("endpoint", "turn", turn.id, "rev", turn.rev);
        beginFinish(turn);
    }

    // ============================== 话轮：结束/推测/提交 ==============================

    private void onSpeechEnd(InboundMessage message) {
        var turn = turns.get(message.intOf("turn", -1));
        if (turn == null
                || turn.cancelled
                || turn.committed
                || turn.softEnded
                || !speculative
                || message.intOf("rev", 0) != turn.rev) {
            return;
        }
        turn.softEnded = true;
        turn.speechEndAtMs = now();
        beginFinish(turn);
    }

    /** 向当前上游任务发 finish-task 并等待 task-finished（在虚拟线程上），完成后回到 actor 发 asr_final。 */
    private void beginFinish(VoiceTurn turn) {
        if (turn.finishing) {
            return;
        }
        var asr = turn.asr;
        turn.asr = null;
        var rev = turn.rev;
        if (asr == null) {
            onAsrFinished(turn, rev, 0);
            return;
        }
        turn.finishing = true;
        deps.blocking()
                .execute(
                        () -> {
                            var seconds = asr.finish();
                            actor.execute(() -> onAsrFinished(turn, rev, seconds));
                        });
    }

    private void onAsrFinished(VoiceTurn turn, int rev, int seconds) {
        turn.finishing = false;
        turn.asrSeconds += seconds;
        if (turn.cancelled) {
            return;
        }
        if (turn.resumeAfterFinish) {
            turn.resumeAfterFinish = false;
            reopenAsr(turn);
            return;
        }
        if (rev != turn.rev) {
            return;
        }
        if (!turn.partial.isBlank()) {
            turn.sentences.add(turn.partial);
        }
        turn.partial = "";
        sendAsrFinal(turn);
    }

    private void sendAsrFinal(VoiceTurn turn) {
        var text = turn.stableText();
        var blank = text.isBlank();
        var exit = turn.autoReply && !blank && exitPhrases.contains(normalize(text));
        var reply = turn.autoReply && !blank && !exit;
        turn.asrFinalSent = true;
        turn.asrFinalAtMs = now();
        send(
                "asr_final",
                "turn",
                turn.id,
                "rev",
                turn.rev,
                "text",
                text,
                "exit",
                exit,
                "reply",
                reply);
        if (reply) {
            startReply(turn, text);
        } else {
            maybeEndTurn(turn);
        }
    }

    private void onSpeechResume(InboundMessage message) {
        var turn = turns.get(message.intOf("turn", -1));
        if (turn == null || turn.cancelled || turn.committed) {
            return;
        }
        var rev = message.intOf("rev", turn.rev + 1);
        if (rev <= turn.rev) {
            return;
        }
        turn.rev = rev;
        turn.softEnded = false;
        turn.asrFinalSent = false;
        turn.speechEndAtMs = 0;
        discardReply(turn);
        if (turn.asr != null) {
            return;
        }
        if (turn.finishing) {
            turn.resumeAfterFinish = true;
        } else {
            reopenAsr(turn);
        }
    }

    /** 续说：在新的上游任务（新 task_id）上继续识别，并重放软结束期间暂存的音频。 */
    private void reopenAsr(VoiceTurn turn) {
        if (!openAsr(turn)) {
            return;
        }
        for (var pcm : turn.held) {
            turn.asr.sendAudio(pcm);
        }
        turn.held.clear();
        turn.heldBytes = 0;
    }

    private void onCommit(InboundMessage message) {
        var turn = turns.get(message.intOf("turn", -1));
        if (turn == null
                || turn.cancelled
                || turn.committed
                || message.intOf("rev", turn.rev) != turn.rev) {
            return;
        }
        turn.committed = true;
        turn.held.clear();
        turn.heldBytes = 0;
        if (!turn.softEnded && !turn.finishing && turn.asr != null) {
            turn.speechEndAtMs = now();
            beginFinish(turn);
        } else {
            maybeEndTurn(turn);
        }
    }

    private void onCancel(InboundMessage message) {
        var turn = turns.get(message.intOf("turn", -1));
        if (turn == null || turn.cancelled) {
            return;
        }
        var heard = message.mapOf("heard").get("sentences");
        cancelTurn(
                turn,
                message.strOf("reason", "client_cancel"),
                heard instanceof Number number ? number.intValue() : 0,
                true);
    }

    // ============================== 回复：大脑 ==============================

    private void startReply(VoiceTurn turn, String text) {
        var gen = ++turn.replyGen;
        turn.replyStarted = true;
        turn.replyActive = true;
        turn.replyDone = false;
        turn.splitter = new SentenceSplitter(props.minSentenceChars(), props.maxSentenceChars());
        send("reply_start", "turn", turn.id, "rev", turn.rev);
        deps.blocking()
                .execute(
                        () -> {
                            Flux<String> stream;
                            try {
                                stream = client.reply(text);
                            } catch (RuntimeException exception) {
                                actor.execute(() -> onReplyError(turn, gen, exception));
                                return;
                            }
                            var subscription =
                                    stream.subscribe(
                                            delta ->
                                                    actor.execute(
                                                            () -> onReplyDelta(turn, gen, delta)),
                                            error ->
                                                    actor.execute(
                                                            () -> onReplyError(turn, gen, error)),
                                            () -> actor.execute(() -> onReplyComplete(turn, gen)));
                            actor.execute(
                                    () -> {
                                        if (isCurrentReply(turn, gen)) {
                                            turn.brain = subscription;
                                        } else {
                                            subscription.dispose();
                                        }
                                    });
                        });
    }

    private static boolean isCurrentReply(VoiceTurn turn, int gen) {
        return !turn.cancelled && turn.replyGen == gen;
    }

    private void onReplyDelta(VoiceTurn turn, int gen, String delta) {
        if (!isCurrentReply(turn, gen) || delta == null || delta.isEmpty()) {
            return;
        }
        if (turn.firstDeltaAtMs == 0) {
            turn.firstDeltaAtMs = now();
        }
        turn.replyText.append(delta);
        send("reply_delta", "turn", turn.id, "rev", turn.rev, "delta", delta);
        if (turn.tts) {
            turn.splitter.feed(delta).forEach(sentence -> announce(turn, sentence));
            pumpTts(turn);
        }
    }

    private void onReplyComplete(VoiceTurn turn, int gen) {
        if (!isCurrentReply(turn, gen)) {
            return;
        }
        finishReply(turn);
    }

    private void onReplyError(VoiceTurn turn, int gen, Throwable error) {
        if (!isCurrentReply(turn, gen)) {
            return;
        }
        log.warn("voice 回复失败 client={} turn={}", client.clientKey(), turn.id, error);
        var hadText = !turn.replyText.isEmpty();
        sendError(turn.id, null, codeOf(error), messageOf(error), !hadText);
        if (hadText) {
            finishReply(turn);
            return;
        }
        turn.replyActive = false;
        turn.replyDone = true;
        cancelJobs(turn);
        turn.ended = true;
        settle(turn, "error");
        scheduleRemoval(turn);
    }

    private void finishReply(VoiceTurn turn) {
        if (turn.tts) {
            turn.splitter.flush().forEach(sentence -> announce(turn, sentence));
        }
        turn.replyActive = false;
        turn.replyDone = true;
        send(
                "reply_done",
                "turn",
                turn.id,
                "rev",
                turn.rev,
                "text",
                turn.replyText.toString(),
                "sentences",
                turn.jobs.size());
        pumpTts(turn);
        flushJobs(turn);
        maybeEndTurn(turn);
    }

    /** 撤回推测：丢弃大脑与合成中的一切，回到未回复状态。 */
    private void discardReply(VoiceTurn turn) {
        turn.replyGen++;
        turn.replyStarted = false;
        turn.replyActive = false;
        turn.replyDone = false;
        disposeBrain(turn);
        cancelJobs(turn);
        turn.replyText.setLength(0);
        turn.spokenText.setLength(0);
        turn.splitter = null;
        turn.jobs.clear();
        turn.nextJobToStart = 0;
        turn.jobsInFlight = 0;
        turn.headJob = 0;
    }

    private static void disposeBrain(VoiceTurn turn) {
        if (turn.brain != null) {
            turn.brain.dispose();
            turn.brain = null;
        }
    }

    // ============================== 回复：句子流水线 TTS ==============================

    private void announce(VoiceTurn turn, String text) {
        var job = new SentenceJob(++sidSeq, turn.jobs.size(), text, null);
        turn.jobs.add(job);
        send(
                "tts_sentence",
                "turn",
                turn.id,
                "rev",
                turn.rev,
                "sid",
                job.sid,
                "index",
                job.index,
                "text",
                text,
                "rate",
                SpeechService.REALTIME_TTS_SAMPLE_RATE);
    }

    /** 在并发上限内按顺序启动待合成句子。 */
    private void pumpTts(VoiceTurn turn) {
        while (!turn.cancelled
                && turn.nextJobToStart < turn.jobs.size()
                && turn.jobsInFlight < props.maxTtsInFlight()) {
            var job = turn.jobs.get(turn.nextJobToStart++);
            turn.jobsInFlight++;
            startJob(turn, job);
        }
    }

    private void startJob(VoiceTurn turn, SentenceJob job) {
        job.started = true;
        try {
            job.task =
                    speech.startTtsTask(
                            job.text,
                            voice,
                            new TtsListener() {
                                @Override
                                public void onAudio(byte[] pcm) {
                                    actor.execute(() -> onTtsAudio(turn, job, pcm));
                                }

                                @Override
                                public void onComplete() {
                                    actor.execute(() -> onTtsDone(turn, job, null));
                                }

                                @Override
                                public void onError(Throwable error) {
                                    log.warn(
                                            "voice 上游合成失败 client={} sid={}",
                                            client.clientKey(),
                                            job.sid,
                                            error);
                                    actor.execute(() -> onTtsDone(turn, job, messageOf(error)));
                                }
                            });
        } catch (RuntimeException exception) {
            log.warn("voice 合成任务开启失败 client={} sid={}", client.clientKey(), job.sid, exception);
            onTtsDone(turn, job, messageOf(exception));
        }
    }

    private void onTtsAudio(VoiceTurn turn, SentenceJob job, byte[] pcm) {
        if (job.cancelled || job.done) {
            return;
        }
        if (turn == null) {
            sendPcm(job, pcm);
            return;
        }
        if (turn.firstPcmAtMs == 0) {
            turn.firstPcmAtMs = now();
        }
        if (turn.headJob < turn.jobs.size() && turn.jobs.get(turn.headJob) == job) {
            sendPcm(job, pcm);
        } else {
            job.buffered.addLast(pcm);
        }
    }

    private void onTtsDone(VoiceTurn turn, SentenceJob job, String error) {
        if (job.cancelled || job.done) {
            return;
        }
        job.done = true;
        job.error = error;
        if (turn == null) {
            finishRequest(job);
            return;
        }
        turn.jobsInFlight--;
        if (error == null) {
            turn.spokenText.append(job.text);
        }
        flushJobs(turn);
        pumpTts(turn);
        maybeEndTurn(turn);
    }

    /** 按序放行：队首句子的缓冲音频先发出，已完成则发 tts_sentence_end 并推进队首。 */
    private void flushJobs(VoiceTurn turn) {
        while (turn.headJob < turn.jobs.size()) {
            var job = turn.jobs.get(turn.headJob);
            while (!job.buffered.isEmpty()) {
                sendPcm(job, job.buffered.pollFirst());
            }
            if (!job.done) {
                return;
            }
            sendSentenceEnd(job);
            turn.headJob++;
        }
    }

    private void sendPcm(SentenceJob job, byte[] pcm) {
        var frameMax = props.ttsFrameBytes();
        for (var offset = 0; offset < pcm.length; offset += frameMax) {
            var length = Math.min(frameMax, pcm.length - offset);
            channel.sendBinary(VoiceFrames.encode(FRAME_TTS, job.sid, pcm, offset, length));
        }
    }

    private void sendSentenceEnd(SentenceJob job) {
        if (job.error == null) {
            send("tts_sentence_end", "sid", job.sid);
        } else {
            var error = new LinkedHashMap<String, Object>();
            error.put("code", ERR_GATEWAY_FAILED);
            error.put("message", job.error);
            send("tts_sentence_end", "sid", job.sid, "error", error);
        }
    }

    // ============================== 独立 tts 请求 ==============================

    private void onTtsRequest(InboundMessage message) {
        var req = message.intOf("req", -1);
        var text = message.strOf("text", null);
        if (req < 0 || text == null) {
            sendError(null, req < 0 ? null : req, ERR_BAD_REQUEST, "tts 请求缺少 req 或 text", false);
            return;
        }
        if (ttsRequests.size() >= props.maxActiveTurns()) {
            sendError(null, req, ERR_GATEWAY_FAILED, "并发合成请求过多", true);
            return;
        }
        try {
            client.precheck();
        } catch (RuntimeException exception) {
            sendError(null, req, codeOf(exception), messageOf(exception), true);
            return;
        }
        var job = new SentenceJob(++sidSeq, 0, text, req);
        ttsRequests.put(req, job);
        send(
                "tts_sentence",
                "turn",
                0,
                "req",
                req,
                "sid",
                job.sid,
                "index",
                0,
                "text",
                text,
                "rate",
                SpeechService.REALTIME_TTS_SAMPLE_RATE);
        startJob(null, job);
    }

    private void finishRequest(SentenceJob job) {
        sendSentenceEnd(job);
        ttsRequests.remove(job.req);
        if (job.error == null) {
            var text = job.text;
            deps.blocking().execute(() -> safely(() -> client.settleTts(text)));
        }
    }

    // ============================== 收尾 ==============================

    private void maybeEndTurn(VoiceTurn turn) {
        if (turn.ended || turn.cancelled || !turn.committed || !turn.asrFinalSent) {
            return;
        }
        if (turn.replyStarted && !turn.replyDone) {
            return;
        }
        if (turn.headJob < turn.jobs.size()) {
            return;
        }
        turn.ended = true;
        send("turn_end", "turn", turn.id, "rev", turn.rev);
        settle(turn, null);
        traceTurn(turn);
        scheduleRemoval(turn);
    }

    /** 话轮结束后保留一小段时间，以接收迟到的 cancel(not_adopted) 等消息。 */
    private void scheduleRemoval(VoiceTurn turn) {
        deps.timer()
                .schedule(
                        props.cancelRetentionMs(),
                        () -> actor.execute(() -> turns.remove(turn.id, turn)));
    }

    /**
     * 取消话轮：停止识别/大脑/合成并归还或作废上游连接，按已发生的用量结算。
     *
     * @param notifyClient 是否回调 {@link VoiceClient#onReplyCancelled}
     */
    private void cancelTurn(
            VoiceTurn turn, String reason, int heardSentences, boolean notifyClient) {
        if (turn.cancelled) {
            return;
        }
        turn.cancelled = true;
        turn.replyGen++;
        abortAsr(turn);
        disposeBrain(turn);
        cancelJobs(turn);
        settle(turn, reason);
        if (notifyClient && turn.replyStarted) {
            var replyText = turn.replyText.toString();
            deps.blocking()
                    .execute(
                            () ->
                                    safely(
                                            () ->
                                                    client.onReplyCancelled(
                                                            reason, replyText, heardSentences)));
        }
        turns.remove(turn.id, turn);
    }

    private void abortAsr(VoiceTurn turn) {
        var asr = turn.asr;
        turn.asr = null;
        turn.held.clear();
        turn.heldBytes = 0;
        if (asr != null) {
            deps.blocking().execute(asr::abort);
        }
    }

    private static void cancelJobs(VoiceTurn turn) {
        for (var job : turn.jobs) {
            job.cancelled = true;
            if (job.task != null) {
                job.task.cancel();
            }
        }
    }

    /**
     * 按话轮结算（只结算一次）：识别按上游累计时长（缺失时按收到的字节估算），合成按实际交付的文本。只有 {@code commit} 之后的话轮才计费；设备没有采纳的回复（{@code
     * not_adopted} / {@code exit} / {@code privacy}）不收合成费。
     */
    private void settle(VoiceTurn turn, String reason) {
        if (turn.settled) {
            return;
        }
        turn.settled = true;
        if (!turn.committed) {
            return;
        }
        var durationMs = turn.asrSeconds * 1000;
        if (durationMs <= 0 && turn.audioBytes > 0) {
            durationMs = (int) Math.min(turn.audioBytes * 1000L / 32_000L, Integer.MAX_VALUE);
        }
        var asrMs = durationMs;
        var spoken = isNotAdopted(reason) ? "" : turn.spokenText.toString();
        deps.blocking()
                .execute(
                        () -> {
                            if (asrMs > 0) {
                                safely(() -> client.settleAsr(asrMs));
                            }
                            if (!spoken.isBlank()) {
                                safely(() -> client.settleTts(spoken));
                            }
                        });
    }

    private static boolean isNotAdopted(String reason) {
        return "not_adopted".equals(reason) || "exit".equals(reason) || "privacy".equals(reason);
    }

    private void traceTurn(VoiceTurn turn) {
        log.info(
                "voice_trace client={} turn={} start→firstPartial={}ms speechEnd→asrFinal={}ms"
                        + " asrFinal→firstDelta={}ms asrFinal→firstPcm={}ms",
                client.clientKey(),
                turn.id,
                delta(turn.startedAtMs, turn.firstPartialAtMs),
                delta(turn.speechEndAtMs, turn.asrFinalAtMs),
                delta(turn.asrFinalAtMs, turn.firstDeltaAtMs),
                delta(turn.asrFinalAtMs, turn.firstPcmAtMs));
    }

    private static long delta(long from, long to) {
        return from == 0 || to == 0 ? -1 : to - from;
    }

    private void shutdown() {
        if (closed) {
            return;
        }
        closed = true;
        helloTimer.run();
        for (var turn : new ArrayList<>(turns.values())) {
            cancelTurn(turn, "disconnect", 0, true);
        }
        for (var job : ttsRequests.values()) {
            job.cancelled = true;
            if (job.task != null) {
                job.task.cancel();
            }
        }
        ttsRequests.clear();
        deps.blocking().execute(() -> safely(client::onClose));
        actor.close();
        log.info("voice_session closed client={} session={}", client.clientKey(), id);
    }

    // ============================== 工具 ==============================

    private boolean hasActiveWork() {
        return activeTurns() > 0 || !ttsRequests.isEmpty();
    }

    private int activeTurns() {
        return (int) turns.values().stream().filter(turn -> !turn.cancelled && !turn.ended).count();
    }

    private void touch() {
        lastActivityMs = now();
    }

    private long now() {
        return deps.clock().getAsLong();
    }

    private void send(String type, Object... keyValues) {
        var body = new LinkedHashMap<String, Object>();
        body.put("type", type);
        for (var i = 0; i + 1 < keyValues.length; i += 2) {
            body.put((String) keyValues[i], keyValues[i + 1]);
        }
        channel.sendText(JsonUtils.toJsonString(body));
    }

    private void sendError(Integer turn, Integer req, int code, String message, boolean fallback) {
        var body = new LinkedHashMap<String, Object>();
        body.put("type", "error");
        if (turn != null) {
            body.put("turn", turn);
        }
        if (req != null) {
            body.put("req", req);
        }
        body.put("code", code);
        body.put("message", message);
        body.put("fallback", fallback);
        channel.sendText(JsonUtils.toJsonString(body));
    }

    private static int codeOf(Throwable error) {
        return error instanceof BusinessException business
                ? business.getCode()
                : ERR_GATEWAY_FAILED;
    }

    /** 业务异常给出可读信息；其余统一为通用提示，不向客户端泄露内部细节。 */
    private static String messageOf(Throwable error) {
        if (error instanceof BusinessException business) {
            return business.getMessage();
        }
        return error instanceof SpeechPoolExhaustedException ? "语音服务繁忙，请稍后重试" : "语音服务暂不可用";
    }

    /** 去掉标点与空白并转小写，用于退出口令匹配。 */
    static String normalize(String text) {
        var builder = new StringBuilder();
        for (var i = 0; i < text.length(); i++) {
            var ch = text.charAt(i);
            if (Character.isLetterOrDigit(ch)) {
                builder.append(Character.toLowerCase(ch));
            }
        }
        return builder.toString();
    }

    private static void safely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            log.warn("voice 客户端回调失败", exception);
        }
    }
}
