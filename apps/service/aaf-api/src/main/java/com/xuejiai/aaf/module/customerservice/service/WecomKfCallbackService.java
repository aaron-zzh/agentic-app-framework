package com.xuejiai.aaf.module.customerservice.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.module.customerservice.config.WecomKfProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 回调事件处理：验签、解密、分发 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WecomKfCallbackService {

    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("<Token><!\\[CDATA\\[(.*?)]]></Token>");
    private static final Pattern OPEN_KFID_PATTERN =
            Pattern.compile("<OpenKfId><!\\[CDATA\\[(.*?)]]></OpenKfId>");
    private static final Pattern ENCRYPT_PATTERN =
            Pattern.compile("<Encrypt><!\\[CDATA\\[(.*?)]]></Encrypt>");

    private final WecomKfProperties properties;
    private final WecomKfMessageHandler messageHandler;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    /** URL验证：解密echostr并返回明文 */
    public String verifyUrl(String msgSignature, String timestamp, String nonce, String echostr) {
        // 验证签名
        if (!verifySignature(msgSignature, timestamp, nonce, echostr)) {
            log.warn("URL验证签名失败");
            return "";
        }
        return decrypt(echostr);
    }

    /** 处理事件回调 */
    public void handleEvent(String msgSignature, String timestamp, String nonce, String xmlBody) {
        // 提取加密内容
        var encryptMatcher = ENCRYPT_PATTERN.matcher(xmlBody);
        if (!encryptMatcher.find()) {
            log.warn("回调XML中未找到Encrypt字段");
            return;
        }
        var encrypt = encryptMatcher.group(1);

        // 验证签名
        if (!verifySignature(msgSignature, timestamp, nonce, encrypt)) {
            log.warn("事件回调签名验证失败");
            return;
        }

        // 解密
        var decrypted = decrypt(encrypt);
        log.debug("解密后的回调内容: {}", decrypted);

        // 提取Token和OpenKfId
        var tokenMatcher = TOKEN_PATTERN.matcher(decrypted);
        var kfIdMatcher = OPEN_KFID_PATTERN.matcher(decrypted);

        String token = tokenMatcher.find() ? tokenMatcher.group(1) : null;
        String openKfId = kfIdMatcher.find() ? kfIdMatcher.group(1) : null;

        if (openKfId == null) {
            log.warn("回调中未找到OpenKfId");
            return;
        }

        // 异步处理消息（快速响应企微服务器）
        executor.submit(
                () -> {
                    try {
                        messageHandler.handleCallback(openKfId, token);
                    } catch (Exception e) {
                        log.error("处理客服消息异常: openKfId={}", openKfId, e);
                    }
                });
    }

    /** 验证签名 */
    private boolean verifySignature(
            String msgSignature, String timestamp, String nonce, String encrypt) {
        try {
            String[] arr = {properties.getToken(), timestamp, nonce, encrypt};
            Arrays.sort(arr);
            var sb = new StringBuilder();
            for (var s : arr) sb.append(s);
            var sha1 = MessageDigest.getInstance("SHA-1");
            var digest = sha1.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            var hexStr = bytesToHex(digest);
            return hexStr.equals(msgSignature);
        } catch (Exception e) {
            log.error("验证签名异常", e);
            return false;
        }
    }

    /** AES解密 */
    private String decrypt(String encrypted) {
        try {
            var aesKey = Base64.getDecoder().decode(properties.getEncodingAesKey() + "=");
            var iv = Arrays.copyOfRange(aesKey, 0, 16);
            var cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new IvParameterSpec(iv));
            var decrypted = cipher.doFinal(Base64.getDecoder().decode(encrypted));
            // 去除补位：前16字节随机串，4字节消息长度，后面是消息内容+corpId
            int msgLen =
                    ((decrypted[16] & 0xFF) << 24)
                            | ((decrypted[17] & 0xFF) << 16)
                            | ((decrypted[18] & 0xFF) << 8)
                            | (decrypted[19] & 0xFF);
            return new String(decrypted, 20, msgLen, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("AES解密失败", e);
            return "";
        }
    }

    private static String bytesToHex(byte[] bytes) {
        var sb = new StringBuilder();
        for (var b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
