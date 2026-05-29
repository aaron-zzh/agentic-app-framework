package com.xuejiai.aaf.module.customerservice.model.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

/** send_msg 请求体 */
public record SendMsgRequest(
        String touser,
        @JsonProperty("open_kfid") String openKfId,
        String msgtype,
        Map<String, Object> text) {}
