package com.xuejiai.aaf.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * 分层架构约束测试。
 *
 * <p>利用 ArchUnit 在编译期自动检测代码是否违反分层架构规则。每次执行单元测试时， 会扫描 {@code com.xuejiai.aaf} 包下所有 class
 * 文件，验证包之间的依赖方向是否合规。 违规代码会导致测试失败，无需等待人工 Code Review 即可发现架构腐化。
 *
 * <p>当前守护的规则：
 *
 * <ol>
 *   <li>domain 层禁止依赖 controller / service / repository（领域模型保持纯净）
 *   <li>controller 层禁止跨越 service 直接调用 repository（必须经过业务层）
 *   <li>controller 层禁止直接依赖 domain 实体（通过 VO 交互，解耦接口与领域）
 *   <li>业务模块之间禁止直接访问对方内部（需通过 api 包暴露的接口交互）
 *   <li>framework 层禁止依赖业务模块（依赖方向：module → framework → common）
 * </ol>
 *
 * <p>对应规范：{@code docs/reference/dev/architecture-constraints.md}。
 *
 * <p>命名 {@code *Test.java} 属 developer 单测范畴，Surefire 执行，归入 {@code pnpm check}。
 */
@AnalyzeClasses(
        packages = "com.xuejiai.aaf",
        importOptions = {ImportOption.DoNotIncludeTests.class})
class LayeringTest {

    /** 规则 1：domain 层禁止依赖 controller / service / repository */
    @ArchTest
    static final ArchRule domainShouldNotDependOnUpperLayers =
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
    static final ArchRule controllerShouldNotAccessRepository =
            noClasses()
                    .that()
                    .resideInAPackage("..module..controller..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("..module..repository..")
                    .as("controller 层禁止跨越 service 直接调用 repository");

    /** 规则 3：controller 层禁止直接依赖 domain 实体 */
    @ArchTest
    static final ArchRule controllerShouldNotDependOnDomain =
            noClasses()
                    .that()
                    .resideInAPackage("..module..controller..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("..module..domain..")
                    .as("controller 层禁止直接依赖 domain 实体，应通过 VO 交互");

    /** 规则 4：业务模块之间禁止直接访问对方的 domain / repository / service */
    @ArchTest
    static final ArchRule modulesShouldNotAccessEachOtherInternals =
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
    static final ArchRule frameworkShouldNotDependOnModules =
            noClasses()
                    .that()
                    .resideInAPackage("com.xuejiai.aaf.framework..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("com.xuejiai.aaf.module..")
                    .as("framework 层禁止依赖业务模块（依赖方向：module → framework → common）");

    /** 规则 6：跨模块依赖只允许访问目标模块的 api 包 */
    @ArchTest
    static final ArchRule crossModuleOnlyViaApi =
            com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices()
                    .matching("com.xuejiai.aaf.module.(*)..")
                    .should()
                    .notDependOnEachOther()
                    .ignoreDependency(
                            com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage(
                                    "..module.."),
                            com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage(
                                    "..module..api.."))
                    .as("跨模块依赖只允许通过 api 包");
}
