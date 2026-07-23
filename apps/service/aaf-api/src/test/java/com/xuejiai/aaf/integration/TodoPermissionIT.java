package com.xuejiai.aaf.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.xuejiai.aaf.common.enums.sys.TodoStatusEnum;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.cache.PermissionCacheService;
import com.xuejiai.aaf.module.system.org.domain.OrgMember;
import com.xuejiai.aaf.module.system.org.domain.Organization;
import com.xuejiai.aaf.module.system.org.repository.OrgMemberRepository;
import com.xuejiai.aaf.module.system.org.repository.OrganizationRepository;
import com.xuejiai.aaf.module.system.permission.domain.PermissionCode;
import com.xuejiai.aaf.module.system.permission.repository.PermissionCodeRepository;
import com.xuejiai.aaf.module.system.role.domain.Role;
import com.xuejiai.aaf.module.system.role.domain.RolePermission;
import com.xuejiai.aaf.module.system.role.domain.UserRole;
import com.xuejiai.aaf.module.system.role.repository.RolePermissionRepository;
import com.xuejiai.aaf.module.system.role.repository.RoleRepository;
import com.xuejiai.aaf.module.system.role.repository.UserRoleRepository;
import com.xuejiai.aaf.module.system.task.domain.Todo;
import com.xuejiai.aaf.module.system.task.repository.TodoRepository;
import com.xuejiai.aaf.module.system.user.domain.User;
import com.xuejiai.aaf.module.system.user.repository.UserRepository;

/**
 * Todo 模块四层权限集成测试。
 *
 * <p>覆盖 L1（功能权限码 / 角色）、L2（ReBAC 关系元组）、L3（行级 assigneeId 隔离）， 及 super_admin 绕过逻辑的组合效果。
 *
 * <p>鉴权判断（{@code PermissionSecurityService}/{@code DataAccessService}）均直查数据库角色关系， 不读取 Spring
 * Security 的 authority 字符串；因此每个用例必须真实写入 {@code sys_user_role} / {@code sys_role_permission}，不能仅靠
 * {@code .with(user(...).roles(...))} 模拟。
 *
 * <p>不依赖 db/seed、db/testdata 的固定种子数据，用例自行构造并在 {@link #tearDown()} 中清理， 避免与环境既有数据/其他测试相互影响。
 *
 * @author AaronZZH & Kiro
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TodoPermissionIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private RolePermissionRepository rolePermissionRepository;
    @Autowired private PermissionCodeRepository permissionCodeRepository;
    @Autowired private TodoRepository todoRepository;
    @Autowired private PermissionCacheService permissionCacheService;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private OrgMemberRepository orgMemberRepository;

    private User userA;
    private User userB;
    private Todo todoOfA;
    private Role memberRole;
    private Organization testOrg;

    @BeforeEach
    void setUp() {
        // 本测试类的准备/清理逻辑均直接调 repository（非经 mockMvc 请求），OrgContext
        // 无 orgId；Role/PermissionCode/Organization 等实体虽继承 BaseEntity 带 orgFilter，
        // 但此处属于测试数据准备阶段，用 runIgnoring 整体豁免 fail-closed 校验（与
        // BuiltinRoleInitializer 用 @OrgIgnore 是同一类场景，测试代码非 Spring bean 方法调用，
        // 改用编程式豁免）。真正验证租户隔离效果的请求均经 mockMvc.perform 携带 X-Org-Id。
        OrgContext.runIgnoring(
                () -> {
                    memberRole = findOrCreateRole("todo_it_member");
                    grantPermission(memberRole, "system:todo:read");
                    grantPermission(memberRole, "system:todo:create");
                    grantPermission(memberRole, "system:todo:update");
                    grantPermission(memberRole, "system:todo:delete");
                    grantPermission(memberRole, "system:todo:export");
                    grantPermission(memberRole, "system:todo:reference");

                    userA = createUser("todo_it_user_a");
                    userB = createUser("todo_it_user_b");
                    bindRole(userA, memberRole);
                    bindRole(userB, memberRole);

                    grantPermission(memberRole, "system:todo:access-mode:admin-maintenance");

                    testOrg = createOrgWithMembers(userA, userB);

                    todoOfA = new Todo();
                    todoOfA.setAssigneeId(userA.getId());
                    todoOfA.setOrgId(testOrg.getId());
                    todoOfA.setTitle("A 的待办");
                    todoOfA = todoRepository.save(todoOfA);
                });
        // PermissionSecurityService 按 userId 缓存权限结果（Redis，TTL 5min），
        // 而角色/权限绑定在此处走 Repository 直接写入，不经过 PermissionService，
        // 不会触发 bumpPermissionVersion。若 userId 曾在缓存 TTL 内被判定为无权限
        // （如先前测试用同一自增 ID 走过无权限分支），会残留 __EMPTY__ 污染本次用例。
        permissionCacheService.evict(userA.getId());
        permissionCacheService.evict(userB.getId());
    }

    @AfterEach
    void tearDown() {
        OrgContext.runIgnoring(
                () -> {
                    todoRepository.deleteAll(List.of(todoOfA));
                    userRoleRepository
                            .findByUserIdAndDeletedFalse(userA.getId())
                            .forEach(userRoleRepository::delete);
                    userRoleRepository
                            .findByUserIdAndDeletedFalse(userB.getId())
                            .forEach(userRoleRepository::delete);
                    orgMemberRepository
                            .findByOrgIdAndDeletedFalse(testOrg.getId())
                            .forEach(orgMemberRepository::delete);
                    organizationRepository.deleteById(testOrg.getId());
                    userRepository.deleteAll(List.of(userA, userB));
                    rolePermissionRepository
                            .findByRoleIdInAndDeletedFalse(List.of(memberRole.getId()))
                            .forEach(rolePermissionRepository::delete);
                    roleRepository.deleteAll(List.of(memberRole));
                });
        permissionCacheService.evict(userA.getId());
        permissionCacheService.evict(userB.getId());
    }

    // ==================== L3 行级隔离 ====================

    @Test
    @DisplayName("Given B 非执行人 When 查询 A 的待办详情 Then 404（L3 过滤）")
    void should_return_404_when_non_assignee_queries_todo() throws Exception {
        mockMvc.perform(
                        get("/api/todos/{id}", todoOfA.getId())
                                .header("X-Org-Id", testOrg.getId().toString())
                                .with(user(userB.getId().toString())))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Given A 是执行人 When 查询自己的待办详情 Then 200")
    void should_return_200_when_assignee_queries_own_todo() throws Exception {
        mockMvc.perform(
                        get("/api/todos/{id}", todoOfA.getId())
                                .header("X-Org-Id", testOrg.getId().toString())
                                .with(user(userA.getId().toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(todoOfA.getId()));
    }

    // ==================== L1：hasPermission(null, code) ====================

    @Test
    @DisplayName("Given 用户无 system:todo:update 权限码 When 更新待办状态 Then 403")
    void should_return_403_when_updating_status_without_permission_code() throws Exception {
        var userC =
                OrgContext.runIgnoring(() -> createUserInOrg("todo_it_user_c")); // 无任何角色绑定 → 无权限码
        try {
            mockMvc.perform(
                            put("/api/todos/{id}/status", todoOfA.getId())
                                    .header("X-Org-Id", testOrg.getId().toString())
                                    .with(user(userC.getId().toString()))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"status\":\"done\",\"expectedVersion\":0}"))
                    .andExpect(status().isForbidden());
        } finally {
            OrgContext.runIgnoring(() -> userRepository.delete(userC));
        }
    }

    @Test
    @DisplayName("Given A 持有 system:todo:update 权限码 When 更新自己待办状态 Then 200")
    void should_return_200_when_updating_own_status_with_permission_code() throws Exception {
        mockMvc.perform(
                        put("/api/todos/{id}/status", todoOfA.getId())
                                .header("X-Org-Id", testOrg.getId().toString())
                                .with(user(userA.getId().toString()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"done\",\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(TodoStatusEnum.DONE.getCode()));
    }

    @Test
    @DisplayName("Given B 持有权限码但非执行人 When 更新 A 的待办状态 Then 404（L1 通过、L3 拦截）")
    void should_return_404_when_permitted_user_updates_others_todo() throws Exception {
        // B 与 A 同样绑定 memberRole，持有 system:todo:update 权限码（L1 通过），
        // 但 todoOfA.assigneeId = A，L3 行级规则应在 update 时以 404 拦截，验证权限提升边界。
        mockMvc.perform(
                        put("/api/todos/{id}/status", todoOfA.getId())
                                .header("X-Org-Id", testOrg.getId().toString())
                                .with(user(userB.getId().toString()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"done\",\"expectedVersion\":0}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Given A 查询实体关联待办列表 When 持有 system:todo:read 权限码 Then 200")
    void should_return_200_when_listing_by_entity_with_permission_code() throws Exception {
        todoOfA.setSourceEntity("system.todo");
        todoOfA.setSourceId(todoOfA.getId());
        OrgContext.runIgnoring(() -> todoRepository.save(todoOfA));

        mockMvc.perform(
                        get("/api/todos/by-entity/{entity}/{id}", "system.todo", todoOfA.getId())
                                .header("X-Org-Id", testOrg.getId().toString())
                                .with(user(userA.getId().toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(todoOfA.getId()));
    }

    @Test
    @DisplayName("Given 用户无 system:todo:read 权限码 When 查询实体关联待办列表 Then 403")
    void should_return_403_when_listing_by_entity_without_permission_code() throws Exception {
        var userC = OrgContext.runIgnoring(() -> createUserInOrg("todo_it_user_c"));
        try {
            mockMvc.perform(
                            get(
                                            "/api/todos/by-entity/{entity}/{id}",
                                            "system.todo",
                                            todoOfA.getId())
                                    .header("X-Org-Id", testOrg.getId().toString())
                                    .with(user(userC.getId().toString())))
                    .andExpect(status().isForbidden());
        } finally {
            OrgContext.runIgnoring(() -> userRepository.delete(userC));
        }
    }

    // ==================== L1：hasRole（管理端维护动作） ====================

    @Test
    @DisplayName("Given 非管理员角色 When 调用批量清理已完成待办 Then 403")
    void should_return_403_when_non_admin_clears_done_todos() throws Exception {
        mockMvc.perform(
                        put("/api/todos/_clear-done")
                                .header("X-Org-Id", testOrg.getId().toString())
                                .with(user(userB.getId().toString())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Given ADMIN 角色 When 调用批量清理已完成待办 Then 200 且仅清理 done 状态")
    void should_clear_only_done_todos_when_admin_calls() throws Exception {
        // hasAnyRole('ADMIN','SUPER_ADMIN') 只读 Spring Security authority，不查数据库角色关系，
        // 故此处仅需在请求上下文附加 ROLE_ADMIN authority，无需真实绑定 sys_user_role。
        var doneTodo = new Todo();
        doneTodo.setAssigneeId(userB.getId());
        doneTodo.setOrgId(testOrg.getId());
        doneTodo.setTitle("B 已完成的待办");
        doneTodo.setStatus(TodoStatusEnum.DONE.getCode());
        var savedDoneTodo = OrgContext.runIgnoring(() -> todoRepository.save(doneTodo));

        try {
            mockMvc.perform(
                            put("/api/todos/_clear-done")
                                    .header("X-Org-Id", testOrg.getId().toString())
                                    .with(
                                            user(userA.getId().toString())
                                                    .authorities(
                                                            new SimpleGrantedAuthority(
                                                                    "ROLE_ADMIN"))))
                    .andExpect(status().isOk());

            OrgContext.runIgnoring(
                    () -> {
                        Assertions.assertThat(todoRepository.findById(savedDoneTodo.getId()))
                                .isEmpty();
                        Assertions.assertThat(todoRepository.findById(todoOfA.getId())).isPresent();
                    });
        } finally {
            OrgContext.runIgnoring(() -> todoRepository.deleteById(savedDoneTodo.getId()));
        }
    }

    @Test
    @DisplayName("Given 用户无 system:todo:read 权限码 When 标准 GET 查询待办 Then 403（L1 拒绝）")
    void should_return_403_when_standard_get_has_no_read_permission() throws Exception {
        var userC = OrgContext.runIgnoring(() -> createUserInOrg("todo_it_standard_get_no_read"));
        try {
            mockMvc.perform(
                            get("/api/todos/{id}", todoOfA.getId())
                                    .header("X-Org-Id", testOrg.getId().toString())
                                    .with(user(userC.getId().toString())))
                    .andExpect(status().isForbidden());
        } finally {
            OrgContext.runIgnoring(
                    () -> {
                        orgMemberRepository
                                .findByOrgIdAndDeletedFalse(testOrg.getId())
                                .stream()
                                .filter(member -> member.getUserId().equals(userC.getId()))
                                .forEach(orgMemberRepository::delete);
                        userRepository.delete(userC);
                    });
            permissionCacheService.evict(userC.getId());
        }
    }

    @Test
    @DisplayName("Given A 是执行人但待办属于其他租户 When 使用当前租户标准 GET Then 404")
    void should_return_404_when_standard_get_crosses_tenant_boundary() throws Exception {
        var otherOrg = OrgContext.runIgnoring(() -> createOrgWithMembers(userA));
        var unsavedTodo = new Todo();
        unsavedTodo.setAssigneeId(userA.getId());
        unsavedTodo.setOrgId(otherOrg.getId());
        unsavedTodo.setTitle("其他租户的待办");
        var otherTenantTodo = OrgContext.runIgnoring(() -> todoRepository.save(unsavedTodo));
        try {
            mockMvc.perform(
                            get("/api/todos/{id}", otherTenantTodo.getId())
                                    .header("X-Org-Id", testOrg.getId().toString())
                                    .with(user(userA.getId().toString())))
                    .andExpect(status().isNotFound());
        } finally {
            OrgContext.runIgnoring(
                    () -> {
                        todoRepository.delete(otherTenantTodo);
                        orgMemberRepository
                                .findByOrgIdAndDeletedFalse(otherOrg.getId())
                                .forEach(orgMemberRepository::delete);
                        organizationRepository.delete(otherOrg);
                    });
        }
    }

    // ==================== L2：ReBAC 关系元组 ====================

    @Test
    @DisplayName("Given B 未被授予关系 When 通过标准 GET 查询 A 的待办 Then 404")
    void should_return_404_when_standard_get_has_no_relation() throws Exception {
        mockMvc.perform(
                        get("/api/todos/{id}", todoOfA.getId())
                                .header("X-Org-Id", testOrg.getId().toString())
                                .with(user(userB.getId().toString())))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Given A 分享 VIEWER 关系给 B When B 使用标准 GET Then 可读但更新仍被 L3 拒绝")
    void should_allow_standard_get_but_not_update_after_share() throws Exception {
        mockMvc.perform(
                        post("/api/todos/{id}/share", todoOfA.getId())
                                .header("X-Org-Id", testOrg.getId().toString())
                                .with(user(userA.getId().toString()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"subjectId":%d,"relation":"VIEWER"}
                                        """
                                                .formatted(userB.getId())))
                .andExpect(status().isOk());

        mockMvc.perform(
                        get("/api/todos/{id}", todoOfA.getId())
                                .header("X-Org-Id", testOrg.getId().toString())
                                .with(user(userB.getId().toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(todoOfA.getId()));

        mockMvc.perform(
                        put("/api/todos/{id}/status", todoOfA.getId())
                                .header("X-Org-Id", testOrg.getId().toString())
                                .with(user(userB.getId().toString()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"done\",\"expectedVersion\":0}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Given 用户无 system:todo:update 权限码 When 发起分享 Then 403（分享入口本身受 L1 保护）")
    void should_return_403_when_sharing_without_permission_code() throws Exception {
        var userC = OrgContext.runIgnoring(() -> createUserInOrg("todo_it_user_c"));
        try {
            mockMvc.perform(
                            post("/api/todos/{id}/share", todoOfA.getId())
                                    .header("X-Org-Id", testOrg.getId().toString())
                                    .with(user(userC.getId().toString()))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            """
                                            {"subjectId":%d,"relation":"VIEWER"}
                                            """
                                                    .formatted(userB.getId())))
                    .andExpect(status().isForbidden());
        } finally {
            OrgContext.runIgnoring(() -> userRepository.delete(userC));
        }
    }

    // ==================== 标准 CRUD：Controller 仅认证，Service PEP 执行 L1/L4 ====================

    @Test
    @DisplayName("Given system:todo:read 权限码已注册 When 无权限码用户查询列表 Then 403（严格校验分支）")
    void should_return_403_when_listing_todos_without_registered_permission_code()
            throws Exception {
        // setUp 已注册 system:todo:read 并只授予 memberRole，userC 无任何角色 → 命中严格分支
        var userC = OrgContext.runIgnoring(() -> createUserInOrg("todo_it_user_c"));
        try {
            mockMvc.perform(
                            get("/api/todos")
                                    .header("X-Org-Id", testOrg.getId().toString())
                                    .with(user(userC.getId().toString())))
                    .andExpect(status().isForbidden());
        } finally {
            OrgContext.runIgnoring(() -> userRepository.delete(userC));
        }
    }

    @Test
    @DisplayName("Given system:todo:create 权限码已注册 When 无权限码用户创建待办 Then 403")
    void should_return_403_when_creating_todo_without_permission_code() throws Exception {
        var userC = OrgContext.runIgnoring(() -> createUserInOrg("todo_it_user_c"));
        try {
            mockMvc.perform(
                            post("/api/todos")
                                    .header("X-Org-Id", testOrg.getId().toString())
                                    .with(user(userC.getId().toString()))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            """
                                            {"title":"无权限待办"}
                                            """))
                    .andExpect(status().isForbidden());
        } finally {
            OrgContext.runIgnoring(() -> userRepository.delete(userC));
        }
    }

    // ==================== super_admin 快速通道 ====================

    @Test
    @DisplayName("Given super_admin 角色 When 查询任意人的待办详情 Then 200（绕过 L3）")
    void should_return_200_when_super_admin_queries_others_todo() throws Exception {
        var superAdminRole = OrgContext.runIgnoring(() -> findOrCreateRole("super_admin"));
        OrgContext.runIgnoring(() -> bindRole(userB, superAdminRole));
        try {
            mockMvc.perform(
                            get("/api/todos/{id}", todoOfA.getId())
                                    .header("X-Org-Id", testOrg.getId().toString())
                                    .with(
                                            user(userB.getId().toString())
                                                    .authorities(
                                                            new SimpleGrantedAuthority(
                                                                    "ROLE_SUPER_ADMIN"))))
                    .andExpect(status().isOk());
        } finally {
            OrgContext.runIgnoring(
                    () ->
                            userRoleRepository.findByUserIdAndDeletedFalse(userB.getId()).stream()
                                    .filter(ur -> ur.getRoleId().equals(superAdminRole.getId()))
                                    .forEach(userRoleRepository::delete));
        }
    }

    private User createUser(String username) {
        var user = new User();
        user.setUsername(username);
        user.setNickname(username);
        user.setEmail(username + "@it-test.local");
        user.setPassword("N/A");
        user.setEmailVerified(true);
        return userRepository.save(user);
    }

    /** 创建用户并加入 {@link #testOrg}——orgFilter 已改为 fail-closed，临时用户也需组织归属才能过校验。 */
    private User createUserInOrg(String username) {
        var user = createUser(username);
        var member = new OrgMember();
        member.setOrgId(testOrg.getId());
        member.setUserId(user.getId());
        member.setRole("member");
        orgMemberRepository.save(member);
        return user;
    }

    private Role findOrCreateRole(String code) {
        return roleRepository
                .findByCodeAndDeletedFalse(code)
                .orElseGet(
                        () -> {
                            var role = new Role();
                            role.setCode(code);
                            role.setName(code);
                            return roleRepository.save(role);
                        });
    }

    private void bindRole(User user, Role role) {
        var userRole = new UserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleId(role.getId());
        userRoleRepository.save(userRole);
    }

    private void grantPermission(Role role, String code) {
        var permission =
                permissionCodeRepository
                        .findByCodeAndDeletedFalseAndStatus(code, 0)
                        .orElseGet(
                                () -> {
                                    var p = new PermissionCode();
                                    p.setName(code);
                                    p.setCode(code);
                                    var parts = code.split(":");
                                    p.setModule(parts[0]);
                                    p.setResource(parts[1]);
                                    p.setAction(parts[2]);
                                    p.setStatus(0);
                                    return permissionCodeRepository.save(p);
                                });
        var rolePermission = new RolePermission();
        rolePermission.setRoleId(role.getId());
        rolePermission.setPermissionId(permission.getId());
        rolePermissionRepository.save(rolePermission);
    }

    /** 创建测试用团队组织，并将给定用户加入为 member，返回组织实体（含 orgId 供请求头使用）。 */
    private Organization createOrgWithMembers(User... users) {
        var org = new Organization();
        org.setName("todo_it_test_org");
        org.setSlug("todo-it-test-org-" + System.nanoTime());
        org.setType("team");
        org.setOwnerId(users[0].getId());
        org = organizationRepository.save(org);
        for (var user : users) {
            var member = new OrgMember();
            member.setOrgId(org.getId());
            member.setUserId(user.getId());
            member.setRole("member");
            orgMemberRepository.save(member);
        }
        return org;
    }
}
