package com.xuejiai.aaf.module.system.auth.service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.Base64;
import java.util.Random;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.module.system.auth.vo.CaptchaVO;

import lombok.RequiredArgsConstructor;

/**
 * 图形验证码服务（生成 + Redis 存储 + 校验）。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
public class CaptchaService {

    private static final String CACHE_PREFIX = "captcha:";
    private static final Duration CAPTCHA_TTL = Duration.ofMinutes(5);
    private static final int CODE_LENGTH = 4;
    private static final String CHARS = "0123456789ABCDEFGHJKLMNPQRSTUVWXYZ";

    private final StringRedisTemplate redisTemplate;

    /**
     * 生成图形验证码。
     *
     * @return 验证码 ID 和 Base64 图片
     */
    public CaptchaVO generate() {
        var code = randomCode();
        var captchaId = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(CACHE_PREFIX + captchaId, code, CAPTCHA_TTL);
        var base64Image = generateImage(code);
        return new CaptchaVO(captchaId, base64Image);
    }

    /**
     * 校验验证码。
     *
     * @param captchaId 验证码 ID
     * @param code 用户输入
     * @return 是否正确
     */
    public boolean verify(String captchaId, String code) {
        var key = CACHE_PREFIX + captchaId;
        var cached = redisTemplate.opsForValue().get(key);
        if (cached == null) return false;
        redisTemplate.delete(key);
        return cached.equalsIgnoreCase(code);
    }

    private String randomCode() {
        var random = new Random();
        var sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    private String generateImage(String code) {
        int width = 120, height = 40;
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        var random = new Random();

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.setFont(new Font("Arial", Font.BOLD, 28));

        // 绘制干扰线
        for (int i = 0; i < 5; i++) {
            g.setColor(new Color(random.nextInt(200), random.nextInt(200), random.nextInt(200)));
            g.drawLine(
                    random.nextInt(width),
                    random.nextInt(height),
                    random.nextInt(width),
                    random.nextInt(height));
        }

        // 绘制验证码字符
        for (int i = 0; i < code.length(); i++) {
            g.setColor(new Color(random.nextInt(150), random.nextInt(150), random.nextInt(150)));
            g.drawString(String.valueOf(code.charAt(i)), 20 + i * 25, 30);
        }
        g.dispose();

        try (var baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "PNG", baos);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("生成验证码图片失败", e);
        }
    }
}
