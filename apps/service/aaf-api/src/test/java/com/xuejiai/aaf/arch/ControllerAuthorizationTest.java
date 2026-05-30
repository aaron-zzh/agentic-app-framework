package com.xuejiai.aaf.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import java.util.List;
import java.util.Set;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.freeze.FreezingArchRule;

/**
 * 鉴权默认拒绝基线：{@code @RestController} 中的写接口（POST/PUT/DELETE/PATCH）必须带方法级授权注解。
 *
 * <p>授权注解任一即可：{@code @PreAuthorize} / {@code @AccessControl} / {@code @Secured}。
 * 公开端点与自验签回调通过 {@link #ALLOWLISTED_CONTROLLERS} 显式豁免，新增豁免必须登记并说明理由。
 *
 * <p>用 {@link FreezingArchRule} 冻结当前存量违规为基线：本规则**只拦截新增**未鉴权写接口，
 * 不会因历史欠债（见 B9，约 112 个控制器）导致 {@code pnpm check} 立即变红。
 * 存量控制器按 {@code 10-authorization-matrix.md} 逐个加注解后，冻结基线会自动收缩。
 *
 * <p>首次运行自动生成基线存储（{@code archunit.properties} 中 allowStoreCreation=true）。
 * 对应规范：{@code docs/design/audit/2026-05-30-service-review/10-authorization-matrix.md}（B9/B10）。
 *
 * <p>命名 {@code *Test.java} 属 developer 单测范畴，Surefire 执行，归入 {@code pnpm check}。
 */
@AnalyzeClasses(
        packages = "com.xuejiai.aaf",
        importOptions = {ImportOption.DoNotIncludeTests.class})
class ControllerAuthorizationTest {

    private static final String REST_CONTROLLER =
            "org.springframework.web.bind.annotation.RestController";

    private static final List<String> WRITE_MAPPINGS =
            List.of(
                    "org.springframework.web.bind.annotation.PostMapping",
                    "org.springframework.web.bind.annotation.PutMapping",
                    "org.springframework.web.bind.annotation.DeleteMapping",
                    "org.springframework.web.bind.annotation.PatchMapping");

    private static final List<String> AUTH_ANNOTATIONS =
            List.of(
                    "org.springframework.security.access.prepost.PreAuthorize",
                    "org.springframework.security.access.annotation.Secured",
                    "com.xuejiai.aaf.framework.security.access.AccessControl");

    /** 豁免控制器：公开端点 / 自验签回调 / 示例代码。新增项必须在此显式登记并写明理由。 */
    private static final Set<String> ALLOWLISTED_CONTROLLERS =
            Set.of(
                    "AuthController", // 登录/注册/刷新，公开
                    "CaptchaController", // 验证码，公开
                    "WecomKfCallbackController", // 企微回调，服务内自验签
                    "HelloController", // 健康检查
                    "MovieRestController", // examples 示例（应移出生产）
                    "ImageExampleController", // examples 示例
                    "AgentScopeExampleController"); // examples 示例

    // 谓词/条件须在规则字段之前声明：静态初始化按文本顺序执行，否则规则初始化时引用到 null。
    private static final DescribedPredicate<JavaMethod> ARE_WRITE_ENDPOINTS_IN_REST_CONTROLLERS =
            new DescribedPredicate<>("@RestController 中的写接口") {
                @Override
                public boolean test(JavaMethod method) {
                    return method.getOwner().isAnnotatedWith(REST_CONTROLLER)
                            && WRITE_MAPPINGS.stream().anyMatch(method::isAnnotatedWith);
                }
            };

    private static final ArchCondition<JavaMethod> BE_AUTHORIZED_OR_ALLOWLISTED =
            new ArchCondition<>("带方法级授权注解或在豁免名单内") {
                @Override
                public void check(JavaMethod method, ConditionEvents events) {
                    String controller = method.getOwner().getSimpleName();
                    boolean authorized =
                            AUTH_ANNOTATIONS.stream().anyMatch(method::isAnnotatedWith);
                    if (!authorized && !ALLOWLISTED_CONTROLLERS.contains(controller)) {
                        events.add(
                                SimpleConditionEvent.violated(
                                        method,
                                        "%s.%s() 写接口缺少授权注解（@PreAuthorize/@AccessControl/@Secured）"
                                                .formatted(controller, method.getName())));
                    }
                }
            };

    @ArchTest
    static final ArchRule writeEndpointsMustBeAuthorized =
            FreezingArchRule.freeze(
                    methods()
                            .that(ARE_WRITE_ENDPOINTS_IN_REST_CONTROLLERS)
                            .should(BE_AUTHORIZED_OR_ALLOWLISTED)
                            .as(
                                    "非白名单 @RestController 的写接口(POST/PUT/DELETE/PATCH)必须带方法级授权注解")
                            .because(
                                    "默认拒绝基线，阻止新增未鉴权写接口（见 10-authorization-matrix.md, B9/B10）"));
}
