package com.xuejiai.aaf.module.ai.aigc.execution.service;

import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcActionCommand;
import com.xuejiai.aaf.module.ai.aigc.execution.domain.AigcExecutionBinding;
import com.xuejiai.aaf.module.ai.aigc.execution.domain.AigcExecutionRun;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectObjectView;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectView;

public record AigcActionContext(
        AigcProjectView project,
        AigcProjectObjectView object,
        AigcExecutionBinding binding,
        AigcExecutionRun executionRun,
        AigcActionCommand command) {}
