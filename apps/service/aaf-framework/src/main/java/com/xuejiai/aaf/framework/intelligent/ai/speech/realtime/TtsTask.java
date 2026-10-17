package com.xuejiai.aaf.framework.intelligent.ai.speech.realtime;

/** 一个上游实时合成任务（一段文本），对应一次借出的池化连接。 */
public interface TtsTask {

    /** 取消合成：不再回调监听器，向上游发送取消指令；上游在限定时间内返回结束事件则连接归还复用，否则关闭连接。幂等，不阻塞调用线程。 */
    void cancel();
}
