package com.xuejiai.aaf.framework.intelligent.ai.realtime;

/** 单个实时多模态对话句柄。 */
public interface RealtimeConversation extends AutoCloseable {

    /** 发送音频数据。 */
    void sendAudio(byte[] audio);

    /** 发送文本输入。 */
    void sendText(String text);

    /** 发送图片帧数据。 */
    void sendImage(byte[] image);

    /** 手动提交输入缓冲区。 */
    void commit();

    /** 提交调用方执行函数后的输出，不自动触发下一次响应。 */
    void submitFunctionOutput(String callId, String output);

    /** 手动触发模型响应。 */
    void createResponse();

    /** 取消正在进行的响应。 */
    void cancelResponse();

    /** 获取会话 ID。 */
    String getSessionId();

    /** 关闭会话。 */
    @Override
    void close();
}
