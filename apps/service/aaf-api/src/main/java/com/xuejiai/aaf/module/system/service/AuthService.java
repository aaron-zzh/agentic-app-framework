package com.xuejiai.aaf.module.system.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.system.ErrorCodeConstants.*;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.security.ActorContext;
import com.xuejiai.aaf.framework.security.JwtUtils;
import com.xuejiai.aaf.module.system.domain.User;
import com.xuejiai.aaf.module.system.repository.UserRepository;
import com.xuejiai.aaf.module.system.vo.AuthLoginDTO;
import com.xuejiai.aaf.module.system.vo.AuthLoginVO;

import lombok.RequiredArgsConstructor;

/** 认证业务逻辑。 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final ActorContext actorContext;

    /** 获取当前登录用户 ID */
    public Long currentUserId() {
        return actorContext.currentUserId()
                .orElseThrow(() -> exception(AUTH_TOKEN_EXPIRED));
    }

    /** 账号密码登录 */
    public AuthLoginVO login(AuthLoginDTO dto) {
        User user =
                userRepository
                        .findByUsername(dto.username())
                        .orElseThrow(() -> exception(AUTH_LOGIN_BAD_CREDENTIALS));
        if (!user.isActive()) {
            throw exception(AUTH_LOGIN_USER_DISABLED);
        }
        if (!user.checkPassword(passwordEncoder, dto.password())) {
            throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
        }
        return generateTokens(user);
    }

    /** 刷新令牌 */
    public AuthLoginVO refresh(String refreshToken) {
        Long userId = jwtUtils.validateRefreshToken(refreshToken);
        if (userId == null) {
            throw exception(AUTH_TOKEN_EXPIRED);
        }
        User user =
                userRepository.findById(userId).orElseThrow(() -> exception(AUTH_TOKEN_EXPIRED));
        if (!user.isActive()) {
            jwtUtils.revokeRefreshToken(refreshToken);
            throw exception(AUTH_LOGIN_USER_DISABLED);
        }
        // 删除旧 refreshToken，签发新的（旋转刷新）
        jwtUtils.revokeRefreshToken(refreshToken);
        return generateTokens(user);
    }

    /** 登出 */
    public void logout(String refreshToken) {
        jwtUtils.revokeRefreshToken(refreshToken);
    }

    private AuthLoginVO generateTokens(User user) {
        String accessToken = jwtUtils.generateToken(user.getId());
        String refreshToken = jwtUtils.generateRefreshToken(user.getId());
        return new AuthLoginVO(
                user.getId(), accessToken, refreshToken, jwtUtils.getAccessTokenExpiresTime());
    }
}
