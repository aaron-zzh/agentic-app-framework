package com.xuejiai.aaf.module.system.tool;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;

import com.xuejiai.aaf.framework.engine.tool.generator.GeneratedTool;
import com.xuejiai.aaf.framework.engine.tool.generator.GeneratedToolStore;
import com.xuejiai.aaf.framework.engine.tool.generator.ToolBlueprint;

import lombok.RequiredArgsConstructor;

/**
 * GeneratedToolStore 实现——持久化 AI 生成的工具
 *
 * @author AaronZZH & Kiro
 */
@Component
@RequiredArgsConstructor
public class GeneratedToolStoreImpl implements GeneratedToolStore {

    private final GeneratedToolRepository repository;

    @Override
    public void save(GeneratedTool tool) {
        repository.save(tool);
    }

    @Override
    public Optional<GeneratedTool> findByName(String name) {
        return repository.findByName(name);
    }

    @Override
    public List<GeneratedTool> findByCreator(Long userId) {
        return repository.findByCreatorUserIdAndStatus(userId, "active");
    }

    @Override
    public List<GeneratedTool> findShared() {
        return repository.findByVisibilityAndStatus(ToolBlueprint.Visibility.SHARED, "active");
    }

    @Override
    public List<GeneratedTool> findAccessible(Long userId) {
        return repository.findByCreatorUserIdOrVisibility(userId, ToolBlueprint.Visibility.SHARED);
    }

    @Override
    public void updateVisibility(String name, ToolBlueprint.Visibility visibility) {
        repository
                .findByName(name)
                .ifPresent(
                        t -> {
                            t.setVisibility(visibility);
                            repository.save(t);
                        });
    }
}

@Repository
interface GeneratedToolRepository extends JpaRepository<GeneratedTool, Long> {
    Optional<GeneratedTool> findByName(String name);

    List<GeneratedTool> findByCreatorUserIdAndStatus(Long userId, String status);

    List<GeneratedTool> findByVisibilityAndStatus(
            ToolBlueprint.Visibility visibility, String status);

    List<GeneratedTool> findByCreatorUserIdOrVisibility(
            Long userId, ToolBlueprint.Visibility visibility);
}
