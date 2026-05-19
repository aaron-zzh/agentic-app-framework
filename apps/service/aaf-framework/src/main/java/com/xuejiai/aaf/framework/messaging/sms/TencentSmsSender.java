package com.xuejiai.aaf.framework.messaging.sms;

import java.util.Map;

import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.sms.v20210111.SmsClient;
import com.tencentcloudapi.sms.v20210111.models.SendSmsRequest;

import lombok.extern.slf4j.Slf4j;

/** 腾讯云短信发送器。 */
@Slf4j
public class TencentSmsSender implements SmsSender {

    private final SmsClient client;
    private final String appId;
    private final String signName;

    public TencentSmsSender(SmsProperties.TencentConfig config) {
        this.appId = config.appId();
        this.signName = config.signName();
        var credential = new Credential(config.secretId(), config.secretKey());
        this.client = new SmsClient(credential, "ap-guangzhou");
    }

    @Override
    public void send(String phone, String templateCode, Map<String, String> params) {
        try {
            var request = new SendSmsRequest();
            request.setSmsSdkAppId(appId);
            request.setSignName(signName);
            request.setTemplateId(templateCode);
            request.setPhoneNumberSet(new String[] {"+86" + phone});
            request.setTemplateParamSet(params.values().toArray(new String[0]));

            var response = client.SendSms(request);
            var status = response.getSendStatusSet()[0];
            if (!"Ok".equals(status.getCode())) {
                log.error("腾讯云短信发送失败: code={}, message={}", status.getCode(), status.getMessage());
                throw new RuntimeException("短信发送失败: " + status.getMessage());
            }
            log.info("腾讯云短信发送成功: phone={}, template={}", phone, templateCode);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("腾讯云短信发送异常: phone={}", phone, e);
            throw new RuntimeException("短信发送异常", e);
        }
    }
}
