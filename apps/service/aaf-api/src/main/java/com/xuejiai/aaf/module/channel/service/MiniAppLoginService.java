package com.xuejiai.aaf.module.channel.service;

import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.module.channel.vo.MiniAppLoginDTO;
import com.xuejiai.aaf.module.channel.vo.MiniAppSessionVO;
import com.xuejiai.aaf.framework.security.JwtUtils;
import com.xuejiai.aaf.module.system.user.domain.User;
import com.xuejiai.aaf.module.system.user.domain.UserOauth;
import com.xuejiai.aaf.module.system.user.repository.UserOauthRepository;
import com.xuejiai.aaf.module.system.user.repository.UserRepository;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 微信小程序登录服务。
 *
 * <p>jscode2session → openid → 查/建 UserOauth + User → 签发 JWT。
 * 复用现有 UserOauth 表（provider='wechat_mini'）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MiniAppLoginService {

    private static final String PROVIDER = "wechat_mini";

    private final WxMaService wxMaService;
    private final UserOauthRepository userOauthRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    /**
     * 小程序登录。
     *
     * @param dto 包含 js_code
     * @return 会话信息（accessToken + openid）
     */
    @Transactional
    public MiniAppSessionVO login(MiniAppLoginDTO dto) {
        // 1. jscode2session 换取 openid + session_key
        WxMaJscode2SessionResult session;
        try {
            session = wxMaService.getUserService().getSessionInfo(dto.code());
        } catch (Exception e) {
            log.error("小程序 jscode2session 失败: {}", e.getMessage());
            throw new com.xuejiai.aaf.common.exception.BusinessException(
                    com.xuejiai.aaf.common.exception.GlobalErrorCode.BAD_REQUEST,
                    "小程序登录失败: " + e.getMessage());
        }

        String openid = session.getOpenid();
        String unionid = session.getUnionid();

        // 2. 查找已绑定的 UserOauth
        var oauthOpt = userOauthRepository.findByProviderAndProviderUserId(PROVIDER, openid);

        User user;
        if (oauthOpt.isPresent()) {
            // 已绑定，直接获取用户
            user = userRepository.findById(oauthOpt.get().getUserId())
                    .orElseThrow(() -> new com.xuejiai.aaf.common.exception.BusinessException(
                            com.xuejiai.aaf.common.exception.GlobalErrorCode.NOT_FOUND,
                            "用户不存在"));
        } else {
            // 未绑定，自动创建用户 + 绑定
            user = createMiniAppUser(openid);
            createOAuthBinding(user.getId(), openid, unionid);
        }

        // 3. 记录登录 + 签发 JWT
        user.recordLoginSuccess(null);
        userRepository.save(user);
        String accessToken = jwtUtils.generateToken(user.getId());

        return new MiniAppSessionVO(accessToken, openid, user.getId());
    }

    private User createMiniAppUser(String openid) {
        var user = new User();
        user.setUsername("wx_mini_" + openid.substring(0, Math.min(openid.length(), 8)));
        user.setNickname("微信用户");
        user.setPassword(
                passwordEncoder.encode(String.valueOf(ThreadLocalRandom.current().nextLong())));
        user.setEmailVerified(false);
        return userRepository.save(user);
    }

    private void createOAuthBinding(Long userId, String openid, String unionid) {
        var oauth = new UserOauth();
        oauth.setUserId(userId);
        oauth.setProvider(PROVIDER);
        oauth.setProviderUserId(openid);
        oauth.setProviderUsername(unionid);
        oauth.setTokenExpireTime(LocalDateTime.now().plusDays(30));
        userOauthRepository.save(oauth);
    }
}
