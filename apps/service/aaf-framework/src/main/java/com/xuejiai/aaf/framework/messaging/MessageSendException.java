package com.xuejiai.aaf.framework.messaging;

/**
 * 消息同步发送失败异常。
 *
 * <p>由 {@link MessageService#sendSync(MessageRequest)} 在 ChannelSender 抛错时抛出，承载具体渠道便于上层按渠道返回不同业务错误码。
 *
 * @author AaronZZH &amp; Kiro
 */
public class MessageSendException extends RuntimeException {

    private final MessageChannel channel;

    public MessageSendException(String message, MessageChannel channel, Throwable cause) {
        super(message, cause);
        this.channel = channel;
    }

    public MessageChannel channel() {
        return channel;
    }
}
