package com.xuejiai.aaf.module.content.service.action;

/**
 * 内容动作执行器 SPI。
 *
 * @author AaronZZH & Kiro
 */
public interface ContentActionExecutor {

    boolean supports(String targetType);

    void execute(ContentActionContext context);
}
