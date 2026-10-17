package com.xuejiai.aaf.module.device.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.module.device.ErrorCodeConstants;
import com.xuejiai.aaf.module.device.scenario.DeviceScenario;
import com.xuejiai.aaf.module.device.scenario.DeviceScenarioRegistry;
import com.xuejiai.aaf.module.device.scenario.GeneralDeviceScenario;
import com.xuejiai.aaf.module.device.scenario.OfficeDeviceScenario;

/** 验证内置基础场景与设备场景主体校验的提示。 */
class DeviceScenarioSubjectTest {

    private static final String ELDER_SUBJECT = "elder.profile.elder";

    private final DeviceScenario subjectScenario = new SubjectScenario(null);
    private final DeviceScenario hintedScenario = new SubjectScenario("请先新建档案");

    @Test
    @DisplayName("Given 内置基础场景 When 注册 Then key 唯一且不需要场景主体")
    void should_register_builtin_scenarios_without_subject() {
        // 准备参数
        var general = new GeneralDeviceScenario();
        var office = new OfficeDeviceScenario();

        // 调用
        var registry = new DeviceScenarioRegistry(List.of(general, office));

        // 断言
        assertThat(registry.find("general")).containsSame(general);
        assertThat(registry.find("office")).containsSame(office);
        assertThat(general.supportedSubjectResourceKey()).isNull();
        assertThat(office.supportedSubjectResourceKey()).isNull();
        assertThat(general.commandCapabilities()).isEmpty();
        assertThat(office.commandCapabilities()).isEmpty();
    }

    @Test
    @DisplayName("Given 基础场景 When 不带主体绑定 Then 通过")
    void should_accept_scenario_without_subject_when_no_subject_given() {
        assertThatCode(
                        () ->
                                DeviceService.requireSubjectMatches(
                                        new GeneralDeviceScenario(), null, null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Given 基础场景 When 带了主体 Then 提示清空场景主体")
    void should_reject_subject_when_scenario_does_not_use_one() {
        assertThatThrownBy(
                        () ->
                                DeviceService.requireSubjectMatches(
                                        new OfficeDeviceScenario(), ELDER_SUBJECT, 7L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getCode())
                                        .isEqualTo(
                                                ErrorCodeConstants
                                                        .DEVICE_SCENARIO_SUBJECT_UNSUPPORTED
                                                        .code()));
    }

    @Test
    @DisplayName("Given 需要主体的场景 When 没选主体 Then 提示先新建档案并带场景自己的说明")
    void should_ask_to_add_subject_when_scenario_requires_one() {
        assertThatThrownBy(() -> DeviceService.requireSubjectMatches(hintedScenario, null, null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> {
                            assertThat(exception.getCode())
                                    .isEqualTo(
                                            ErrorCodeConstants.DEVICE_SCENARIO_SUBJECT_REQUIRED
                                                    .code());
                            assertThat(exception.getMessage()).contains("需要主体").contains("请先新建档案");
                        });
    }

    @Test
    @DisplayName("Given 需要主体的场景无自定义提示 When 没选主体 Then 使用通用提示")
    void should_use_generic_hint_when_scenario_has_none() {
        assertThatThrownBy(() -> DeviceService.requireSubjectMatches(subjectScenario, null, null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getMessage()).contains("请先创建对应的主体档案"));
    }

    @Test
    @DisplayName("Given 需要主体的场景 When 主体类型不符 Then 仍按绑定无效拒绝")
    void should_reject_wrong_subject_type() {
        assertThatThrownBy(() -> DeviceService.requireSubjectMatches(subjectScenario, "other", 7L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getCode())
                                        .isEqualTo(
                                                ErrorCodeConstants.DEVICE_SCENARIO_BINDING_INVALID
                                                        .code()));
    }

    @Test
    @DisplayName("Given 需要主体的场景 When 主体匹配 Then 通过")
    void should_accept_matching_subject() {
        assertThatCode(
                        () ->
                                DeviceService.requireSubjectMatches(
                                        subjectScenario, ELDER_SUBJECT, 7L))
                .doesNotThrowAnyException();
    }

    /** 声明了主体类型的测试场景。 */
    private record SubjectScenario(String hint) implements DeviceScenario {

        @Override
        public String key() {
            return "subjected";
        }

        @Override
        public String name() {
            return "需要主体";
        }

        @Override
        public String defaultRoleKey() {
            return null;
        }

        @Override
        public String supportedSubjectResourceKey() {
            return ELDER_SUBJECT;
        }

        @Override
        public String subjectRequiredHint() {
            return hint;
        }
    }
}
