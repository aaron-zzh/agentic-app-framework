package com.xuejiai.aaf.arch;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * 源码扫描：禁止 {@code "ROLE_<lowercase>"} 形式的字符串字面量。
 *
 * <p>背景：{@code SecurityConfig#toRoleAuthority} 强制把 JWT 中的 role code 大写后拼接为 {@code "ROLE_" +
 * UPPER}（如 {@code ROLE_SUPER_ADMIN}）。如果代码里写小写比较（如 {@code "ROLE_super_admin"}）， 与 token 实际 authority
 * 不匹配，会让管理员判定全部失败（如 {@link
 * com.xuejiai.aaf.module.system.dashboard.controller.DashboardController#isAdmin()} 之前的 BUG）。
 *
 * <p>本测试扫描全部 {@code src/main/java/**\/*.java}，禁止匹配正则 {@code "ROLE_[a-z][A-Za-z0-9_]*"} 的字面量。 {@code
 * "ROLE_<UPPER>..."}、列名 {@code role_id} / 注解参数 {@code columnNames = {"role_id"}} 都不会命中（前者已是大写，后者无
 * {@code ROLE_} 前缀）。
 *
 * <p>命名 {@code *Test.java} 属 developer 单测范畴，Surefire 执行，归入 {@code pnpm check}。
 *
 * @author AaronZZH &amp; Kiro
 */
class RoleAuthorityLiteralTest {

    /** 匹配带小写后缀的 ROLE_ 字面量（注意首字符必须是小写字母）。 */
    private static final Pattern LOWERCASE_ROLE_LITERAL =
            Pattern.compile("\"ROLE_[a-z][A-Za-z0-9_]*\"");

    /** 待扫描的源码根目录（相对于各 Maven 模块工作目录）。 */
    private static final List<String> SOURCE_ROOTS =
            List.of(
                    "../aaf-api/src/main/java",
                    "../aaf-framework/src/main/java",
                    "../aaf-auto-dev/src/main/java",
                    "../aaf-common/src/main/java");

    @Test
    void roleAuthorityLiteralsMustBeUppercase() throws IOException {
        var violations = new ArrayList<String>();
        for (var root : SOURCE_ROOTS) {
            Path rootPath = Paths.get(root).toAbsolutePath().normalize();
            if (!Files.isDirectory(rootPath)) {
                continue;
            }
            try (Stream<Path> stream = Files.walk(rootPath)) {
                stream.filter(p -> p.toString().endsWith(".java"))
                        .forEach(
                                p -> {
                                    try {
                                        var content = Files.readString(p);
                                        Matcher m = LOWERCASE_ROLE_LITERAL.matcher(content);
                                        while (m.find()) {
                                            violations.add(
                                                    "%s 含小写 role authority 字面量 %s（应为大写 + 下划线，"
                                                            + "如 \"ROLE_SUPER_ADMIN\"）"
                                                                    .formatted(p, m.group()));
                                        }
                                    } catch (IOException e) {
                                        throw new RuntimeException(e);
                                    }
                                });
            }
        }
        assertTrue(
                violations.isEmpty(),
                "发现 %d 处 ROLE_ 字面量大小写违规：\n  %s\n详见类注释（与 SecurityConfig#toRoleAuthority"
                        + " 输出的大写 authority 必须严格匹配）"
                                .formatted(violations.size(), String.join("\n  ", violations)));
    }
}
