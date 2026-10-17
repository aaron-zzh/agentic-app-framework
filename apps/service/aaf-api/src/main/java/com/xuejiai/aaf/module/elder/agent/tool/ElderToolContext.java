package com.xuejiai.aaf.module.elder.agent.tool;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.elder.profile.api.ElderQueryApi;

import lombok.RequiredArgsConstructor;

/**
 * 养老工具的受信业务上下文。
 *
 * <p>模型参数不接收 elderId。设备网关在 owner 委托上下文内用 {@link #openTrustedElder}
 * 固定绑定长者；普通登录用户仅在指定授权范围下只有一位长者时自动解析。
 */
@Component
@RequiredArgsConstructor
public class ElderToolContext {

    private static final ThreadLocal<TrustedElder> TRUSTED = new ThreadLocal<>();

    private final OperatorContext operatorContext;
    private final ElderQueryApi elderQueryApi;

    public Scope openTrustedElder(Long ownerId, Long elderId, Long deviceId) {
        var currentOwnerId = currentOwnerId();
        if (!Objects.equals(currentOwnerId, ownerId) || elderId == null || elderId <= 0) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "养老工具受信上下文与当前身份不一致");
        }
        var previous = TRUSTED.get();
        TRUSTED.set(new TrustedElder(ownerId, elderId, deviceId));
        return new Scope(previous);
    }

    public ElderQueryApi.AuthorizedElder requireElder(String authorizationScope) {
        var trusted = TRUSTED.get();
        if (trusted != null) {
            if (!Objects.equals(trusted.ownerId(), currentOwnerId())) {
                throw new BusinessException(GlobalErrorCode.FORBIDDEN, "养老工具受信上下文已失效");
            }
            return elderQueryApi.requireAccess(trusted.elderId(), authorizationScope);
        }
        var accessible = elderQueryApi.listAccessibleElders(authorizationScope);
        if (accessible.isEmpty()) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "当前账号没有可访问的长者");
        }
        if (accessible.size() > 1) {
            throw new BusinessException(409, "当前账号可访问多位长者，请由受信调用方先选择长者");
        }
        return accessible.getFirst();
    }

    public Long currentOwnerId() {
        return operatorContext
                .currentOwnerId()
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.UNAUTHORIZED));
    }

    private record TrustedElder(Long ownerId, Long elderId, Long deviceId) {}

    /** 结束后恢复上一层上下文；异步流调用方必须让 scope 覆盖实际订阅执行期。 */
    public static final class Scope implements AutoCloseable {
        private final TrustedElder previous;
        private boolean closed;

        private Scope(TrustedElder previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            if (previous == null) {
                TRUSTED.remove();
            } else {
                TRUSTED.set(previous);
            }
            closed = true;
        }
    }
}
