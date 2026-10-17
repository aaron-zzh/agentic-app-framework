package com.xuejiai.aaf.module.voice.api;

import reactor.core.publisher.Flux;

/**
 * 语音会话的客户端端口：由设备端、网页端各自实现，承载认证主体、计费主体与"大脑"的差异。
 *
 * <p>会话核心（协议、话轮状态机、上游池化）只依赖本接口，不依赖 device / ai 业务包。除 {@link #reply} 外的方法都在会话的串行上下文中调用；
 * 实现不得长时间阻塞。{@link #reply} 与结算方法在虚拟线程上调用，可以阻塞。
 */
public interface VoiceClient {

    /** 会话注册键与日志标识，同一键同时只允许一条会话，如 {@code device:12}、{@code user:42}。 */
    String clientKey();

    /** 开始话轮前的预检（场景启用、积分余额等）。失败抛 {@link com.xuejiai.aaf.common.exception.BusinessException}。 */
    void precheck();

    /**
     * 根据转写文本生成回复，返回文本增量流。订阅方不会阻塞回调。
     *
     * @param text 用户这一轮的转写文本
     */
    Flux<String> reply(String text);

    /** 按识别时长结算（毫秒）。结算失败不得影响会话，实现自行记录。 */
    void settleAsr(int durationMs);

    /** 按实际合成并交付的文本结算语音合成。 */
    void settleTts(String spokenText);

    /**
     * 回复被取消。{@code superseded} / {@code barge_in} 时应把助手历史截断到用户听到的 {@code heardSentences} 句，{@code
     * not_adopted} / {@code exit} / {@code privacy} 时不应把这一轮写入历史。默认无操作。
     */
    default void onReplyCancelled(String reason, String replyText, int heardSentences) {}

    /** 会话关闭后的清理，默认无操作。 */
    default void onClose() {}
}
