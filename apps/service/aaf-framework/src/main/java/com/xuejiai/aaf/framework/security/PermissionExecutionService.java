package com.xuejiai.aaf.framework.security;

import java.util.function.Supplier;

import org.springframework.stereotype.Service;

/** 内部代办/批处理权限执行服务。 */
@Service
public class PermissionExecutionService {

    public <T> T runAsOwner(Long ownerId, String reason, Supplier<T> supplier) {
        try (var ignored = PermissionExecutionContextHolder.useOwner(ownerId, reason)) {
            return supplier.get();
        }
    }

    public void runAsOwner(Long ownerId, String reason, Runnable runnable) {
        try (var ignored = PermissionExecutionContextHolder.useOwner(ownerId, reason)) {
            runnable.run();
        }
    }
}
