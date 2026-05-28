package com.xuejiai.aaf.module.system.permission;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.module.system.role.domain.Role;
import com.xuejiai.aaf.module.system.role.repository.RoleRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 内置角色预置，启动时 upsert。
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BuiltinRoleInitializer implements ApplicationRunner {

    private final RoleRepository roleRepository;

    private record RoleDef(String code, String name, String description) {}

    private static final List<RoleDef> BUILTIN_ROLES =
            List.of(
                    new RoleDef("super_admin", "超级管理员", "拥有系统全部权限"),
                    new RoleDef("org_admin", "组织管理员", "管理组织内用户和资源"),
                    new RoleDef("member", "普通成员", "标准用户权限"),
                    new RoleDef("guest", "访客", "只读权限"),
                    new RoleDef("agent", "AI 智能体", "AI 操作权限"));

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (var def : BUILTIN_ROLES) {
            roleRepository
                    .findByCodeAndDeletedFalse(def.code())
                    .ifPresentOrElse(
                            existing -> {
                                // upsert：更新名称和描述
                                existing.setName(def.name());
                                existing.setDescription(def.description());
                                roleRepository.save(existing);
                            },
                            () -> {
                                var role = new Role();
                                role.setCode(def.code());
                                role.setName(def.name());
                                role.setDescription(def.description());
                                roleRepository.save(role);
                                log.info("预置内置角色: {}", def.code());
                            });
        }
    }
}
