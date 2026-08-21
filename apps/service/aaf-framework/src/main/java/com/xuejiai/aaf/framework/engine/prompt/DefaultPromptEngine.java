package com.xuejiai.aaf.framework.engine.prompt;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.org.OrgIgnore;

import lombok.RequiredArgsConstructor;

/** Prompt 引擎实现：只读取缓存中的已发布不可变 ENGINE 版本。 */
@Service
@RequiredArgsConstructor
@OrgIgnore
public class DefaultPromptEngine implements PromptEngine {

    private final PromptVersionCache promptCache;
    private final PromptTemplateCompiler compiler;

    @Override
    public Optional<CachedPromptVersion> findActive(String code) {
        try {
            return Optional.of(promptCache.requireActive(code))
                    .filter(
                            version ->
                                    version.kind() == PromptKind.ENGINE
                                            && version.visibility() == PromptVisibility.ENGINE);
        } catch (IllegalStateException ignored) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<CachedPromptVersion> findByVersion(String code, int version) {
        try {
            return Optional.of(promptCache.requireVersion(code, version))
                    .filter(
                            candidate ->
                                    candidate.kind() == PromptKind.ENGINE
                                            && candidate.visibility() == PromptVisibility.ENGINE);
        } catch (IllegalStateException ignored) {
            return Optional.empty();
        }
    }

    @Override
    public String render(String code, Map<String, String> variables) {
        return render(requireActive(code), variables);
    }

    @Override
    public String render(String code, int version, Map<String, String> variables) {
        return render(
                findByVersion(code, version)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "ENGINE Prompt 版本不存在: %s@%d"
                                                        .formatted(code, version))),
                variables);
    }

    @Override
    public String chain(List<String> codes, Map<String, String> variables) {
        return codes.stream()
                .map(this::requireActive)
                .map(version -> render(version, variables))
                .collect(java.util.stream.Collectors.joining("\n\n"));
    }

    @Override
    public String renderWithExamples(String code, Map<String, String> variables, int maxExamples) {
        throw new UnsupportedOperationException("Few-shot 示例渲染尚未实现");
    }

    private CachedPromptVersion requireActive(String code) {
        return findActive(code)
                .orElseThrow(() -> new IllegalStateException("ENGINE Prompt 未发布: " + code));
    }

    private String render(CachedPromptVersion version, Map<String, String> variables) {
        return compiler.compile(
                version.content(), version.negativePrompt(), version.variables(), variables, false);
    }
}
