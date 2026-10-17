package com.xuejiai.aaf.module.device.scenario;

import org.springframework.stereotype.Component;

/**
 * 办公/伴学设备场景：只绑定设备所有者，不需要场景主体，也不声明任何业务指令。
 *
 * <p>临时的基础场景，后续再细化；设备端据此启用坐姿提醒等本机算法。对话角色使用设备覆盖或所有者的默认助理角色。
 */
@Component
public class OfficeDeviceScenario implements DeviceScenario {

    public static final String KEY = "office";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String name() {
        return "办公/伴学";
    }

    /** 不指定场景默认角色：沿用设备覆盖或所有者默认助理角色，两者都没有时由角色解析报错。 */
    @Override
    public String defaultRoleKey() {
        return null;
    }
}
