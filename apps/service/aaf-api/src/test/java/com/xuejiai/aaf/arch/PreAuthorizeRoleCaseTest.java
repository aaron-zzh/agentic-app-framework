package com.xuejiai.aaf.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

/**
 * {@code @PreAuthorize} 中 {@code hasRole(...)} / {@code hasAnyRole(...)} 的角色字面量必须全大写 +
 * 下划线。
 *
 * <p>背景：JWT 中的 role code 经 {@code SecurityConfig#toRoleAuthority} 处理时强制 {@code
 * toUpperCase().replace('-', '_')}，最终 authority 形如 {@code ROLE_SUPER_ADMIN}。 Spring Security 的
 * {@code hasRole('xxx')} 仅会前缀拼接为 {@code ROLE_xxx} 不做大小写转换， 因此注解里写小写（如
 * {@code hasRole('super_admin')}）会与 token authority 不匹配，导致拥有该角色的用户被 403。
 *
 * <p>示例：
 *
 * <ul>
 *   <li>✅ 正确：{@code @PreAuthorize("hasRole('SUPER_ADMIN')")}
 *   <li>✅ 正确：{@code @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")}
 *   <li>❌ 错误：{@code @PreAuthorize("hasRole('super_admin')")}
 *   <li>❌ 错误：{@code @PreAuthorize("hasRole('Super_Admin')")}
 * </ul>
 *
 * <p>命名 {@code *Test.java} 属 developer 单测范畴，Surefire 执行，归入 {@code pnpm check}。
 *
 * @author AaronZZH &amp; Kiro
 */
@AnalyzeClasses(
        packages = "com.xuejiai.aaf",
        importOptions = {ImportOption.DoNotIncludeTests.class})
class PreAuthorizeRoleCaseTest {

    private static final String PREAUTHORIZE =
            "org.springframework.security.access.prepost.PreAuthorize";

    /** 匹配 hasRole('xxx') / hasAnyRole('a', 'b'): 抽出每个单引号内的 role code。 */
    private static final Pattern ROLE_LITERAL_PATTERN =
            Pattern.compile("has(?:Any)?Role\\s*\\(([^)]*)\\)");

    private static final Pattern QUOTED_LITERAL_PATTERN = Pattern.compile("'([^']*)'");

    /** 合法的 role code 字面量：仅大写字母 + 数字 + 下划线，不允许小写或连字符。 */
    private static final Pattern UPPERCASE_ROLE = Pattern.compile("^[A-Z][A-Z0-9_]*$");

    private static final ArchCondition<JavaMethod> HAVE_UPPERCASE_ROLE_LITERALS =
            new ArchCondition<>("hasRole / hasAnyRole 参数必须大写 + 下划线") {
                @Override
                public void check(JavaMethod method, ConditionEvents events) {
                    method.getAnnotations().stream()
                            .filter(a -> a.getRawType().getName().equals(PREAUTHORIZE))
                            .map(a -> (String) a.get("value").orElse(""))
                            .forEach(
                                    expr -> {
                                        Matcher callMatcher = ROLE_LITERAL_PATTERN.matcher(expr);
                                        while (callMatcher.find()) {
                                            String args = callMatcher.group(1);
                                            Matcher quoteMatcher =
                                                    QUOTED_LITERAL_PATTERN.matcher(args);
                                            while (quoteMatcher.find()) {
                                                String roleCode = quoteMatcher.group(1);
                                                if (!UPPERCASE_ROLE.matcher(roleCode).matches()) {
                                                    events.add(
                                                            SimpleConditionEvent.violated(
                                                                    method,
                                                                    """
                                                                    %s.%s() @PreAuthorize 表达式 [%s] \
                                                                    中 role code '%s' 必须为大写 + \
                                                                    下划线（如 SUPER_ADMIN），\
                                                                    否则与 ROLE_<UPPER> authority \
                                                                    不匹配会导致 403"""
                                                                            .formatted(
                                                                                    method.getOwner()
                                                                                            .getSimpleName(),
                                                                                    method
                                                                                            .getName(),
                                                                                    expr,
                                                                                    roleCode)));
                                                }
                                            }
                                        }
                                    });
                }
            };

    @ArchTest
    static final ArchRule preAuthorizeRoleLiteralsMustBeUppercase =
            methods()
                    .that()
                    .areAnnotatedWith(PREAUTHORIZE)
                    .should(HAVE_UPPERCASE_ROLE_LITERALS)
                    .as("@PreAuthorize 中 hasRole/hasAnyRole 的角色字面量必须为大写 + 下划线")
                    .because("Spring Security hasRole 不做大小写转换，与 toRoleAuthority toUpperCase 后的"
                            + " authority 必须严格一致，否则授权失败");
}
