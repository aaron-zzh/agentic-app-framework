package com.xuejiai.aaf.module.customerservice.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.module.customerservice.config.WecomKfProperties;
import com.xuejiai.aaf.module.customerservice.service.WecomKfCallbackService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 企微客服回调端点 */
@Tag(name = "企微客服")
@Slf4j
@RestController
@RequestMapping("/api/wecom/kf")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "aaf.wecom.kf", name = "enabled", havingValue = "true")
public class WecomKfCallbackController {

    private final WecomKfCallbackService callbackService;
    private final WecomKfProperties properties;

    /** URL验证（企微配置回调时的GET请求） */
    @Operation(summary = "回调URL验证")
    @GetMapping("/callback")
    public String verify(
            @RequestParam("msg_signature") String msgSignature,
            @RequestParam String timestamp,
            @RequestParam String nonce,
            @RequestParam String echostr) {
        return callbackService.verifyUrl(msgSignature, timestamp, nonce, echostr);
    }

    /** 接收事件回调（企微推送的POST请求） */
    @Operation(summary = "接收企微事件回调")
    @PostMapping("/callback")
    public String receiveCallback(
            @RequestParam("msg_signature") String msgSignature,
            @RequestParam String timestamp,
            @RequestParam String nonce,
            @RequestBody String xmlBody) {
        callbackService.handleEvent(msgSignature, timestamp, nonce, xmlBody);
        return "success";
    }
}
