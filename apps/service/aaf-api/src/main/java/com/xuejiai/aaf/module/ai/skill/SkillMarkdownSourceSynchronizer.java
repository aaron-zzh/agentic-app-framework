package com.xuejiai.aaf.module.ai.skill;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.framework.engine.skill.SkillDefinition;
import com.xuejiai.aaf.framework.engine.skill.SkillToolRequirement;
import com.xuejiai.aaf.framework.engine.skill.SkillVersion;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.SystemSkillBindingEntity;
import com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence.SystemSkillBindingRepository;
import com.xuejiai.aaf.framework.org.OrgIgnore;
import com.xuejiai.aaf.module.ai.skill.SkillMarkdownSourceParser.SkillSource;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 内置技能初始化：技能表为空时，把 {@code aaf/skills/*.md} 加载为数据库种子。
 *
 * <p>只在 {@code ai_skill_definition} 完全为空时执行，已有任何技能数据则整体跳过，不做比对、不覆盖、不补缺。加载顺序固定为：
 * 先创建全部技能（根对象与已批准的初始版本），再为这些版本绑定工具要求，最后写系统级默认技能绑定；三步在同一事务内，任一步失败整体回滚。 运行时仍只通过 {@code SkillStore}
 * 读取数据库中的当前已批准版本。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillMarkdownSourceSynchronizer implements ApplicationRunner {

    private static final String SOURCE_PATTERN = "classpath*:aaf/skills/*.md";
    private static final String APPROVED = "APPROVED";

    private final SkillMarkdownSourceParser parser;
    private final SkillDefinitionRepository definitionRepository;
    private final SkillVersionRepository versionRepository;
    private final SkillToolRequirementRepository toolRequirementRepository;
    private final SkillCategoryRepository categoryRepository;
    private final SystemSkillBindingRepository systemBindingRepository;

    @Override
    @OrgIgnore
    @Transactional
    public void run(ApplicationArguments args) {
        if (definitionRepository.count() > 0) {
            log.info("技能表已有数据，跳过内置技能 Markdown 初始化");
            return;
        }
        var sources = loadSources();
        // 1 先加载技能：根对象 + 已批准的初始版本
        var versionIds = new LinkedHashMap<String, Long>();
        var skillIds = new LinkedHashMap<String, Long>();
        for (var source : sources) {
            var definition = createDefinition(source);
            var version = createVersion(definition.getId(), source);
            definition.setCurrentVersionId(version.getId());
            definitionRepository.save(definition);
            skillIds.put(source.code(), definition.getId());
            versionIds.put(source.code(), version.getId());
        }
        definitionRepository.flush();
        // 2 再绑定工具要求：依赖上一步产生的版本 ID
        for (var source : sources) {
            var versionId = versionIds.get(source.code());
            for (var tool : source.tools()) {
                var requirement = new SkillToolRequirement();
                requirement.setSkillVersionId(versionId);
                requirement.setToolId(tool.toolName());
                requirement.setToolVersion(1L);
                requirement.setToolName(tool.toolName());
                requirement.setRequired(true);
                requirement.setUsagePurpose(tool.usagePurpose());
                requirement.setSortOrder(tool.sortOrder());
                toolRequirementRepository.save(requirement);
            }
        }
        // 3 最后写系统级默认技能绑定：依赖技能 ID
        for (var source : sources) {
            var binding = source.systemBinding();
            if (binding == null) {
                continue;
            }
            var entity = new SystemSkillBindingEntity();
            entity.setSkillId(skillIds.get(source.code()));
            entity.setActivationMode(binding.activationMode());
            entity.setEnabled(true);
            entity.setSortOrder(binding.sortOrder());
            systemBindingRepository.save(entity);
        }
        log.info("已从 Markdown 初始化内置技能 {} 个", sources.size());
    }

    private List<SkillSource> loadSources() {
        try {
            var resources = new PathMatchingResourcePatternResolver().getResources(SOURCE_PATTERN);
            var sources = new ArrayList<SkillSource>();
            for (var resource : resources) {
                sources.add(parser.parse(resource));
            }
            var duplicated =
                    sources.stream()
                            .collect(
                                    Collectors.groupingBy(SkillSource::code, Collectors.counting()))
                            .entrySet()
                            .stream()
                            .filter(entry -> entry.getValue() > 1)
                            .map(Map.Entry::getKey)
                            .toList();
            if (!duplicated.isEmpty()) {
                throw new IllegalStateException("Skill 源文件 code 重复: " + duplicated);
            }
            sources.sort(Comparator.comparing(SkillSource::code));
            return sources;
        } catch (IOException exception) {
            throw new IllegalStateException("Skill Markdown 源文件扫描失败", exception);
        }
    }

    private SkillDefinition createDefinition(SkillSource source) {
        var definition = new SkillDefinition();
        definition.setCode(source.code());
        definition.setName(source.name());
        definition.setSummary(source.summary());
        definition.setInstancePrompt(source.instancePrompt());
        definition.setLocale("zh-CN");
        definition.setVisibility(source.visibility());
        definition.setBuiltIn(true);
        if (!source.categories().isEmpty()) {
            var categories =
                    categoryRepository.findByCodeIn(new java.util.HashSet<>(source.categories()));
            if (categories.size() != source.categories().size()) {
                throw new IllegalStateException("Skill 源引用了不存在的分类: " + source.source());
            }
            definition.getCategories().addAll(categories);
        }
        return definitionRepository.saveAndFlush(definition);
    }

    private SkillVersion createVersion(Long skillId, SkillSource source) {
        var version = new SkillVersion();
        version.setSkillId(skillId);
        version.setVersion(source.version());
        version.setStatus(APPROVED);
        version.setContent(source.content());
        version.setToolAccessMode(source.toolAccessMode());
        version.setChangeSummary(source.changeSummary());
        version.setContentHash(source.contentHash());
        version.setCreateTime(LocalDateTime.now());
        return versionRepository.save(version);
    }
}
