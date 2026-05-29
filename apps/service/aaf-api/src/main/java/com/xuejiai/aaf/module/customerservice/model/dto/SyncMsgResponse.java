package com.xuejiai.aaf.module.customerservice.model.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/** sync_msg 接口响应 */
@Data
public class SyncMsgResponse {

    private int errcode;
    private String errmsg;

    @JsonProperty("next_cursor")
    private String nextCursor;

    @JsonProperty("has_more")
    private int hasMore;

    @JsonProperty("msg_list")
    private List<MsgItem> msgList;

    @Data
    public static class MsgItem {
        private String msgid;

        @JsonProperty("open_kfid")
        private String openKfId;

        @JsonProperty("external_userid")
        private String externalUserId;

        @JsonProperty("send_time")
        private long sendTime;

        /** 3-客户发送 4-系统事件 5-接待人员发送 */
        private int origin;

        @JsonProperty("servicer_userid")
        private String servicerUserId;

        private String msgtype;

        /** 文本消息内容 */
        private Map<String, Object> text;

        /** 事件消息内容 */
        private Map<String, Object> event;
    }
}
