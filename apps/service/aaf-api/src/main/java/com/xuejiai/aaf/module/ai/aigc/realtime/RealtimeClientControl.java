package com.xuejiai.aaf.module.ai.aigc.realtime;

/** v1 已解析的客户端控制事件。 */
public sealed interface RealtimeClientControl
        permits RealtimeClientControl.InputText,
                RealtimeClientControl.InputImage,
                RealtimeClientControl.InputAudioCommit,
                RealtimeClientControl.ResponseCreate,
                RealtimeClientControl.ResponseCancel,
                RealtimeClientControl.SessionClose {

    record InputText(String text) implements RealtimeClientControl {}

    record InputImage(String mimeType, byte[] data) implements RealtimeClientControl {}

    record InputAudioCommit() implements RealtimeClientControl {}

    record ResponseCreate() implements RealtimeClientControl {}

    record ResponseCancel() implements RealtimeClientControl {}

    record SessionClose() implements RealtimeClientControl {}
}
