package com.xuejiai.aaf.framework.messaging.sms;

import java.util.Map;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.teaopenapi.models.Config;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.messaging.ProviderResponse;

import lombok.extern.slf4j.Slf4j;

/** 阿里云短信发送器。 */
@Slf4j
public class AliyunSmsSender implements SmsSender {

    private static final String PROVIDER = "aliyun";

    private final Client client;
    private final String signName;

    public AliyunSmsSender(SmsProperties.AliyunConfig config) {
        this.signName = config.signName();
        try {
            var apiConfig =
                    new Config()
                            .setAccessKeyId(config.accessKeyId())
                            .setAccessKeySecret(config.accessKeySecret())
                            .setEndpoint("dysmsapi.aliyuncs.com");
            this.client = new Client(apiConfig);
        } catch (Exception e) {
            throw new RuntimeException("阿里云短信客户端初始化失败", e);
        }
    }

    @Override
    public ProviderResponse send(String phone, String templateCode, Map<String, String> params) {
        try {
            var paramsJson = JsonUtils.toJsonString(params);
            var request =
                    new SendSmsRequest()
                            .setPhoneNumbers(phone)
                            .setSignName(signName)
                            .setTemplateCode(templateCode)
                            .setTemplateParam(paramsJson);
            var response = client.sendSms(request);
            var body = response.getBody();
            if (!"OK".equals(body.getCode())) {
                log.error(
                        "阿里云短信发送失败: sign={}, code={}, message={}",
                        signName,
                        body.getCode(),
                        body.getMessage());
                throw new RuntimeException("短信发送失败: " + body.getMessage());
            }
            log.info(
                    "阿里云短信发送成功: phone={}, template={}, bizId={}",
                    phone,
                    templateCode,
                    body.getBizId());
            return new ProviderResponse(
                    PROVIDER, body.getBizId(), body.getCode(), body.getMessage());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("阿里云短信发送异常: phone={}", phone, e);
            throw new RuntimeException("短信发送异常", e);
        }
    }
}
