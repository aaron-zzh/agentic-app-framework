package com.xuejiai.aaf.module.ai.aigc.project.api;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAnySetter;

/** Assistant Blueprint 规划前冻结的用户创建表单事实；不包含任何服务端签发身份或配置版本。 */
public record AssistantBlueprintCreationContext(
        String name,
        AssistantBlueprintProposalInput.ProjectSpecification projectSpecification,
        List<Long> brandProfileVersionIds,
        List<Document> documents,
        AigcProjectCoverMode coverMode,
        Long coverFileId,
        String coverPrompt,
        String coverIdempotencyKey) {

    public AssistantBlueprintCreationContext {
        brandProfileVersionIds =
                brandProfileVersionIds == null ? List.of() : List.copyOf(brandProfileVersionIds);
        documents = documents == null ? List.of() : List.copyOf(documents);
    }

    @JsonAnySetter
    public void rejectUnknownField(String field, Object ignored) {
        throw new IllegalArgumentException("creationContext 不接受字段: " + field);
    }

    public List<AigcProjectDocumentInput> documentInputs() {
        return documents.stream()
                .map(
                        document ->
                                new AigcProjectDocumentInput(
                                        document.fieldKey(),
                                        document.documentId(),
                                        document.documentVersionId(),
                                        document.contextNote()))
                .toList();
    }

    /** 用户选择的文档事实；具体引用语义由 Assistant 提议的 DocumentSpec 约束。 */
    public record Document(
            String fieldKey, Long documentId, Long documentVersionId, String contextNote) {

        @JsonAnySetter
        public void rejectUnknownField(String field, Object ignored) {
            throw new IllegalArgumentException("creationContext.documents 不接受字段: " + field);
        }
    }
}
