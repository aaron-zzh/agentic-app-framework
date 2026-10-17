package com.xuejiai.aaf.module.device.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import com.xuejiai.aaf.common.enums.device.DeviceMessageDirectionEnum;
import com.xuejiai.aaf.common.enums.device.DeviceStatusEnum;
import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.module.device.api.DeviceMessageApi.DeviceMessageCommand;
import com.xuejiai.aaf.module.device.domain.Device;
import com.xuejiai.aaf.module.device.domain.DeviceMessage;
import com.xuejiai.aaf.module.device.mqtt.Rfc8785Canonicalizer;
import com.xuejiai.aaf.module.device.repository.DeviceMessageRepository;
import com.xuejiai.aaf.module.device.repository.DeviceRepository;
import com.xuejiai.aaf.module.device.scenario.DeviceScenario;
import com.xuejiai.aaf.module.device.scenario.DeviceScenarioRegistry;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

/** 用与养老无关的第二个场景验证通用设备层的场景扩展契约。 */
class DeviceScenarioContractTest extends BaseMockitoUnitTest {

    private static final String KIDS = "kids";
    private static final String LESSON_SHOW = "lesson.show";

    @Mock private DeviceRepository deviceRepository;
    @Mock private DeviceMessageRepository messageRepository;

    private DefaultDeviceMessageApi api;

    @BeforeEach
    void setUp() {
        var registry = new DeviceScenarioRegistry(List.of(new KidsScenario()));
        api =
                new DefaultDeviceMessageApi(
                        deviceRepository,
                        messageRepository,
                        new DeviceMessageStateService(),
                        registry,
                        new Rfc8785Canonicalizer());
    }

    @Test
    @DisplayName("Given 场景声明了能力 When 下发该能力指令 Then 入队成功")
    void should_enqueue_when_capability_declared_by_scenario() {
        // 准备参数
        stubBoundDevice();
        when(messageRepository.findByCommandId("cmd-1")).thenReturn(Optional.empty());
        when(messageRepository.save(any(DeviceMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // 调用
        var ref = api.enqueue(command(DeviceMessageDirectionEnum.COMMAND, LESSON_SHOW));

        // 断言
        assertThat(ref.commandId()).isEqualTo("cmd-1");
        verify(messageRepository).save(any(DeviceMessage.class));
    }

    @Test
    @DisplayName("Given 场景未声明能力 When 下发其他场景的能力 Then 拒绝且不入队")
    void should_reject_when_capability_not_declared_by_scenario() {
        // 准备参数
        stubBoundDevice();
        var command = command(DeviceMessageDirectionEnum.COMMAND, "reminder.show");

        // 调用与断言
        assertThatThrownBy(() -> api.enqueue(command)).isInstanceOf(BusinessException.class);
        verify(messageRepository, never()).save(any(DeviceMessage.class));
    }

    @Test
    @DisplayName("Given 任意场景 When 下发配置 Then 只认通用的 config.update")
    void should_only_allow_generic_config_capability_for_config_direction() {
        // 准备参数
        stubBoundDevice();
        when(messageRepository.findByCommandId("cmd-1")).thenReturn(Optional.empty());
        when(messageRepository.save(any(DeviceMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // 调用
        var ref = api.enqueue(command(DeviceMessageDirectionEnum.CONFIG, "config.update"));

        // 断言
        assertThat(ref.commandId()).isEqualTo("cmd-1");
        assertThatThrownBy(
                        () -> api.enqueue(command(DeviceMessageDirectionEnum.CONFIG, LESSON_SHOW)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Given 场景不限设备类型 When 按场景主体下发 Then 取主体名下最早绑定的设备")
    void should_resolve_any_bound_device_when_scenario_has_no_device_type() {
        // 准备参数
        var device = boundDevice();
        when(deviceRepository.findFirstByScenarioKeyAndSubjectIdAndStatusOrderByIdAsc(
                        KIDS, 7L, DeviceStatusEnum.BOUND.getCode()))
                .thenReturn(Optional.of(device));
        when(deviceRepository.findLockedById(1L)).thenReturn(Optional.of(device));
        when(messageRepository.findByCommandId("cmd-1")).thenReturn(Optional.empty());
        when(messageRepository.save(any(DeviceMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        var bySubject =
                new DeviceMessageCommand(
                        null,
                        KIDS,
                        7L,
                        "cmd-1",
                        DeviceMessageDirectionEnum.COMMAND.getCode(),
                        LESSON_SHOW,
                        "show",
                        Map.of("lessonId", 1),
                        LocalDateTime.now().plusMinutes(5),
                        null,
                        null);

        // 调用
        var ref = api.enqueue(bySubject);

        // 断言
        assertThat(ref.commandId()).isEqualTo("cmd-1");
    }

    private void stubBoundDevice() {
        when(deviceRepository.findLockedById(1L)).thenReturn(Optional.of(boundDevice()));
    }

    private static Device boundDevice() {
        var device = new Device();
        device.setId(1L);
        device.setDeviceKey("device-k1");
        device.setScenarioKey(KIDS);
        device.setStatus(DeviceStatusEnum.BOUND.getCode());
        device.setOwnerUserId(9L);
        return device;
    }

    private static DeviceMessageCommand command(
            DeviceMessageDirectionEnum direction, String capability) {
        return new DeviceMessageCommand(
                1L,
                null,
                null,
                "cmd-1",
                direction.getCode(),
                capability,
                "show",
                Map.of("lessonId", 1),
                LocalDateTime.now().plusMinutes(5),
                null,
                null);
    }

    /** 假场景：只声明自己的能力，不指定设备类型。 */
    private static final class KidsScenario implements DeviceScenario {

        @Override
        public String key() {
            return KIDS;
        }

        @Override
        public String name() {
            return "儿童教育";
        }

        @Override
        public String defaultRoleKey() {
            return "test.role.kids";
        }

        @Override
        public Set<String> commandCapabilities() {
            return Set.of(LESSON_SHOW);
        }
    }
}
