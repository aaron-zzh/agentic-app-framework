package com.xuejiai.aaf.arch;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;

/**
 * 分层架构约束测试（占位）。
 *
 * <p>对应规范：{@code docs/reference/dev/architecture-constraints.md}。v0.1 骨架 (AAF-023) 完成后，把
 * placeholder_arch_scanning_works 替换为真实的分层规则集合。
 *
 * <p>命名 {@code *Test.java} 属 developer 单测范畴，Surefire 执行，归入 {@code pnpm check}。
 *
 * @see <a
 *     href="../../../../../../../../../../docs/reference/dev/architecture-constraints.md">架构约束</a>
 */
@AnalyzeClasses(
    packages = "com.xuejiai.aaf",
    importOptions = {ImportOption.DoNotIncludeTests.class})
class LayeringTest {

    /**
     * 占位规则：确认 ArchUnit 能扫描到主包，验证工具链通畅。任何被 AAF 框架扫描到的 Java 类都满足此规则（总通过），等分层规范落位后替换为真实约束。
     */
    @ArchTest
    static final ArchRule placeholder_arch_scanning_works =
            ArchRuleDefinition.classes()
                    .that()
                    .resideInAPackage("com.xuejiai.aaf..")
                    .should(
                            new ArchCondition<JavaClass>("be scannable by ArchUnit") {
                                @Override
                                public void check(JavaClass item, ConditionEvents events) {
                                    events.add(
                                            SimpleConditionEvent.satisfied(
                                                    item, "class loaded: " + item.getName()));
                                }
                            })
                    .as("占位：AAF v0.1 骨架就绪后替换为真实分层规则")
                    .allowEmptyShould(true);

    // TODO(AAF-023): 待 aaf-api / aaf-framework 模块包结构稳定后启用以下规则
    // 1) domain 层禁止依赖 infrastructure / gateway / Spring 框架注解
    // 2) controller 层禁止跨越 application 直接调用 domain repository
    // 3) gateway 实现必须位于 domain 定义的接口之下（依赖倒置）
    // 4) 业务模块之间不得通过非 facade 方式互相调用
    // 5) 对称性：每个 @Transactional 方法必须在失败路径有补偿或测试覆盖
}
