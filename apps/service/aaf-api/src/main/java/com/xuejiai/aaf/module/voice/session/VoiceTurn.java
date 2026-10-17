package com.xuejiai.aaf.module.voice.session;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import com.xuejiai.aaf.framework.intelligent.ai.speech.realtime.AsrTask;
import com.xuejiai.aaf.framework.intelligent.ai.speech.realtime.TtsTask;

import reactor.core.Disposable;

/** 一个话轮的可变状态。只在会话的串行上下文中读写，因此不需要同步。 */
final class VoiceTurn {

    final int id;
    final String lang;
    final boolean autoReply;
    final boolean tts;
    final long startedAtMs;

    /** 当前修订号，仅在 speech_resume 时递增。 */
    int rev;

    // ---- 识别 ----
    AsrTask asr;
    int asrSeq;
    final List<String> sentences = new ArrayList<>();
    String partial = "";
    boolean partialDirty;
    boolean partialFlushScheduled;
    long lastPartialAtMs = Long.MIN_VALUE / 2;
    boolean softEnded;
    boolean committed;
    boolean finishing;
    boolean resumeAfterFinish;
    boolean asrFinalSent;
    int asrSeconds;
    long audioBytes;
    final Deque<byte[]> held = new ArrayDeque<>();
    int heldBytes;

    /** 服务端断句：文本版本号，定时器触发时比对以判断期间是否有新文本。 */
    int speechVersion;

    // ---- 回复 ----
    int replyGen;
    boolean replyStarted;
    boolean replyActive;
    boolean replyDone;
    Disposable brain;
    final StringBuilder replyText = new StringBuilder();
    SentenceSplitter splitter;
    final List<SentenceJob> jobs = new ArrayList<>();
    int nextJobToStart;
    int jobsInFlight;
    int headJob;

    // ---- 收尾 ----
    boolean cancelled;
    boolean ended;
    boolean settled;
    final StringBuilder spokenText = new StringBuilder();

    // ---- 耗时（voice_trace）----
    long firstPartialAtMs;
    long speechEndAtMs;
    long asrFinalAtMs;
    long firstDeltaAtMs;
    long firstPcmAtMs;

    VoiceTurn(int id, String lang, boolean autoReply, boolean tts, long startedAtMs) {
        this.id = id;
        this.lang = lang;
        this.autoReply = autoReply;
        this.tts = tts;
        this.startedAtMs = startedAtMs;
    }

    /** 已定稿句子 + 进行中的片段拼成的完整文本。 */
    String fullText() {
        return TextJoiner.join(sentences, partial);
    }

    /** 仅已定稿部分。 */
    String stableText() {
        return TextJoiner.join(sentences, "");
    }

    /** 一句待合成/合成中的回复句子，或一次独立 tts 请求。 */
    static final class SentenceJob {

        final int sid;
        final int index;
        final String text;

        /** 非空表示这是独立 tts 请求的应答。 */
        final Integer req;

        TtsTask task;
        boolean started;
        boolean done;
        boolean cancelled;
        String error;
        final Deque<byte[]> buffered = new ArrayDeque<>();

        SentenceJob(int sid, int index, String text, Integer req) {
            this.sid = sid;
            this.index = index;
            this.text = text;
            this.req = req;
        }
    }
}
