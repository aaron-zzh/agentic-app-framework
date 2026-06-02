package com.xuejiai.aaf.framework.engine.workflow.config;

import org.flowable.bpmn.model.BaseElement;
import org.flowable.bpmn.model.ScriptTask;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.engine.impl.bpmn.parser.BpmnParse;
import org.flowable.engine.impl.bpmn.parser.handler.AbstractBpmnParseHandler;

/** 部署期拒绝 scriptTask 节点——防止通过 BPMN 部署执行任意脚本（RCE）。 */
public class RejectScriptTaskParseHandler extends AbstractBpmnParseHandler<ScriptTask> {

    @Override
    protected Class<? extends BaseElement> getHandledType() {
        return ScriptTask.class;
    }

    @Override
    protected void executeParse(BpmnParse bpmnParse, ScriptTask element) {
        throw new FlowableException("scriptTask 已禁用");
    }
}
