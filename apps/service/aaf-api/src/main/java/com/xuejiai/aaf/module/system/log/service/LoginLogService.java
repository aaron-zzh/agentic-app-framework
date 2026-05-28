package com.xuejiai.aaf.module.system.log.service;

import java.time.LocalDateTime;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.module.system.log.domain.LoginLog;
import com.xuejiai.aaf.module.system.log.repository.LoginLogRepository;
import com.xuejiai.aaf.module.system.log.vo.LoginLogPageDTO;
import com.xuejiai.aaf.module.system.log.vo.LoginLogVO;

import lombok.RequiredArgsConstructor;

/**
 * 登录日志业务逻辑。
 *
 * @author AaronZZH & Kiro
 */
@Service
@RequiredArgsConstructor
public class LoginLogService {

    private final LoginLogRepository loginLogRepository;

    /**
     * 记录登录日志。
     *
     * @param userId 用户 ID（可为 null，登录失败时可能无用户）
     * @param username 用户名
     * @param loginType 登录类型（PASSWORD/EMAIL/OAUTH）
     * @param ip 登录 IP
     * @param userAgent User-Agent
     * @param location IP 归属地
     * @param success 是否成功
     * @param failReason 失败原因
     */
    @Transactional
    public void record(
            Long userId,
            String username,
            String loginType,
            String ip,
            String userAgent,
            String location,
            boolean success,
            String failReason) {
        var log = new LoginLog();
        log.setUserId(userId);
        log.setUsername(username);
        log.setLoginType(loginType);
        log.setIp(ip);
        log.setUserAgent(userAgent);
        log.setLocation(location);
        log.setSuccess(success);
        log.setFailReason(failReason);
        log.setLoginTime(LocalDateTime.now());
        loginLogRepository.save(log);
    }

    /**
     * 分页查询登录日志。
     *
     * @param req 分页查询参数
     * @return 分页结果
     */
    public PageResult<LoginLogVO> page(LoginLogPageDTO req) {
        var pageable = req.toPageable(Sort.by("id").descending());
        Specification<LoginLog> spec =
                SpecificationBuilder.<LoginLog>builder()
                        .likeIfPresent("username", req.getUsername())
                        .likeIfPresent("ip", req.getIp())
                        .eqIfPresent("success", req.getSuccess())
                        .betweenIfPresent("loginTime", req.getStartTime(), req.getEndTime())
                        .build();
        var page = loginLogRepository.findAll(spec, pageable);
        return new PageResult<>(
                page.getContent().stream().map(this::toVO).toList(), page.getTotalElements());
    }

    private LoginLogVO toVO(LoginLog entity) {
        return new LoginLogVO(
                entity.getId(),
                entity.getUserId(),
                entity.getUsername(),
                entity.getLoginType(),
                entity.getIp(),
                entity.getUserAgent(),
                entity.getLocation(),
                entity.getSuccess(),
                entity.getFailReason(),
                entity.getLoginTime());
    }
}
