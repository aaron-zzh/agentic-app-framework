package com.xuejiai.aaf.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 分层架构约束测试。
 *
 * <p>对应规范：{@code docs/reference/dev/architecture-constraints.md}。
 *
 * <p>命名 {@code *Test.java} 属 developer 单测范畴，Surefire 执行，归入 {@code pnpm check}。
 */
@AnalyzeClasses(
        packages = "com.xuejiai.aaf",
        importOptions = {ImportOption.DoNotIncludeTests.class})
class LayeringTest {

    /** 规则 1：domain 层禁止依赖 controller / service / repository / Spring 框架注解 */
    @ArchTest
    static final ArchRule domain_不依赖上层 =
            noClasses()
                    .that()
                    .resideInAPackage("..module..domain..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "..module..controller..",
                            "..module..service..",
                            "..module..repository..")
                    .as("domain 层禁止依赖 controller / service / repository");

    /** 规则 2：controller 层禁止直接调用 repository */
    @ArchTest
    static final ArchRule controller_不直接访问_repository =
            noClasses()
                    .that()
                    .resideInAPackage("..module..controller..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("..module..repository..")
                    .as("controller 层禁止跨越 service 直接调用 repository");

    /** 规则 3：controller 层禁止直接依赖 domain 实体 */
    @ArchTest
    static final ArchRule controller_不直接依赖_domain =
            noClasses()
                    .that()
                    .resideInAPackage("..module..controller..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("..module..domain..")
                    .as("controller 层禁止直接依赖 domain 实体，应通过 VO 交互");

    /** 规则 4：业务模块之间禁止直接访问对方的 domain / repository / service（需通过 api 包） */
    @ArchTest
    static final ArchRule 跨模块禁止直接访问内部 =
            noClasses()
                    .that()
                    .resideInAPackage("..module.system..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "..module.document..domain..",
                            "..module.document..repository..",
                            "..module.document..service..",
                            "..module.chat..domain..",
                            "..module.chat..repository..",
                            "..module.chat..service..")
                    .as("业务模块之间禁止直接访问对方内部（domain/repository/service）");

    /** 规则 5：framework 层禁止依赖业务模块 */
    @ArchTest
    static final ArchRule framework_不依赖业务模块 =
            noClasses()
                    .that()
                    .resideInAPackage("com.xuejiai.aaf.framework..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("com.xuejiai.aaf.module..")
                    .as("framework 层禁止依赖业务模块（依赖方向：module → framework → common）");
}
