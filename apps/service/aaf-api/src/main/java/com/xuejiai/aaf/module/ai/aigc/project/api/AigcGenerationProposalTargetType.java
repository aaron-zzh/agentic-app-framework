package com.xuejiai.aaf.module.ai.aigc.project.api;

/** 生成 Proposal 项的业务目标类型；执行后由对应 candidate 真理源承接结果。 */
public enum AigcGenerationProposalTargetType {
    INITIALIZATION_FIELD,
    PROJECT_DOCUMENT,
    PROJECT_OBJECT,
    PROJECT_COVER
}
