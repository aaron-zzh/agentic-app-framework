package com.xuejiai.aaf.module.content.service.action;

import com.xuejiai.aaf.module.content.domain.ContentExecutionBinding;
import com.xuejiai.aaf.module.content.domain.ContentExecutionRun;
import com.xuejiai.aaf.module.content.domain.ContentProject;
import com.xuejiai.aaf.module.content.domain.ContentProjectObject;
import com.xuejiai.aaf.module.content.vo.ContentActionCommandDTO;

/**
 * 内容动作执行上下文。
 *
 * @author AaronZZH & Kiro
 */
public record ContentActionContext(
        ContentProject project,
        ContentProjectObject object,
        ContentExecutionBinding binding,
        ContentExecutionRun executionRun,
        ContentActionCommandDTO command) {}
