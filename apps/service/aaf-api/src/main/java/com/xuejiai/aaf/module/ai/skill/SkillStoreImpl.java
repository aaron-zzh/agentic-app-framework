package com.xuejiai.aaf.module.ai.skill;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.framework.engine.skill.SkillDefinition;
import com.xuejiai.aaf.framework.engine.skill.SkillModelRequirement;
import com.xuejiai.aaf.framework.engine.skill.SkillStore;
import com.xuejiai.aaf.framework.engine.skill.SkillStore.SkillSummaryRecord;
import com.xuejiai.aaf.framework.engine.skill.SkillToolRequirement;
import com.xuejiai.aaf.framework.engine.skill.SkillVersion;

import lombok.RequiredArgsConstructor;

/** SkillStore 的版本化 JPA 桥接；只暴露当前已审核版本。 */
@Component
@RequiredArgsConstructor
public class SkillStoreImpl implements SkillStore {

    private static final String APPROVED = "APPROVED";

    private final SkillDefinitionRepository repository;
    private final SkillVersionRepository versionRepository;
    private final SkillToolRequirementRepository toolRequirementRepository;
    private final SkillModelRequirementRepository modelRequirementRepository;

    @Override
    public Optional<SkillSummaryRecord> findSummaryByCode(String skillCode) {
        return repository.findApprovedSummaryByCode(skillCode, APPROVED).map(this::toSummary);
    }

    @Override
    public Optional<SkillSummaryRecord> findSummaryBySkillId(Long skillId) {
        return repository.findApprovedSummaryBySkillId(skillId, APPROVED).map(this::toSummary);
    }

    private SkillSummaryRecord toSummary(SkillSummaryProjection projection) {
        return new SkillSummaryRecord(
                projection.getSkillId(),
                projection.getCode(),
                projection.getName(),
                projection.getSummary(),
                projection.getVersionId(),
                projection.getVersion(),
                toolRequirementRepository.findBySkillVersionId(projection.getVersionId()).stream()
                        .map(SkillToolRequirement::getToolName)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()),
                modelRequirementRepository.findBySkillVersionId(projection.getVersionId()).stream()
                        .filter(requirement -> Boolean.TRUE.equals(requirement.getRequired()))
                        .map(SkillModelRequirement::getCapability)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()),
                Boolean.TRUE.equals(projection.getBuiltIn()));
    }

    @Override
    public Optional<SkillRecord> findByCode(String skillCode) {
        return repository.findByCode(skillCode).flatMap(this::toRecord);
    }

    @Override
    public Optional<SkillRecord> findBySkillId(Long skillId) {
        return repository.findById(skillId).flatMap(this::toRecord);
    }

    @Override
    public List<SkillRecord> findBuiltIn() {
        return repository.findByBuiltInTrue().stream()
                .map(this::toRecord)
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<SkillRecord> toRecord(SkillDefinition definition) {
        if (definition.getCurrentVersionId() == null) {
            return Optional.empty();
        }
        return versionRepository
                .findByIdAndStatus(definition.getCurrentVersionId(), APPROVED)
                .filter(version -> version.getSkillId().equals(definition.getId()))
                .map(
                        version ->
                                new SkillRecord(
                                        definition.getId(),
                                        definition.getCode(),
                                        definition.getName(),
                                        definition.getSummary(),
                                        version.getId(),
                                        version.getVersion(),
                                        version.getContent(),
                                        toolRequirementRepository
                                                .findBySkillVersionId(version.getId())
                                                .stream()
                                                .map(SkillToolRequirement::getToolName)
                                                .collect(
                                                        java.util.stream.Collectors
                                                                .toUnmodifiableSet()),
                                        modelRequirementRepository
                                                .findBySkillVersionId(version.getId())
                                                .stream()
                                                .filter(
                                                        requirement ->
                                                                Boolean.TRUE.equals(
                                                                        requirement.getRequired()))
                                                .map(SkillModelRequirement::getCapability)
                                                .collect(
                                                        java.util.stream.Collectors
                                                                .toUnmodifiableSet()),
                                        Boolean.TRUE.equals(definition.getBuiltIn())));
    }
}

@Repository
interface SkillDefinitionRepository extends CrudEntityRepository<SkillDefinition> {
    Optional<SkillDefinition> findByNameAndBuiltInTrue(String name);

    Optional<SkillDefinition> findByCode(String code);

    @Query(
            """
            SELECT d.id AS skillId,
                   d.code AS code,
                   d.name AS name,
                   d.summary AS summary,
                   v.id AS versionId,
                   v.version AS version,
                   d.builtIn AS builtIn
            FROM SkillDefinition d, SkillVersion v
            WHERE d.code = :code
              AND d.currentVersionId = v.id
              AND v.skillId = d.id
              AND v.status = :status
            """)
    Optional<SkillSummaryProjection> findApprovedSummaryByCode(
            @Param("code") String code, @Param("status") String status);

    @Query(
            """
            SELECT d.id AS skillId,
                   d.code AS code,
                   d.name AS name,
                   d.summary AS summary,
                   v.id AS versionId,
                   v.version AS version,
                   d.builtIn AS builtIn
            FROM SkillDefinition d, SkillVersion v
            WHERE d.id = :skillId
              AND d.currentVersionId = v.id
              AND v.skillId = d.id
              AND v.status = :status
            """)
    Optional<SkillSummaryProjection> findApprovedSummaryBySkillId(
            @Param("skillId") Long skillId, @Param("status") String status);

    List<SkillDefinition> findByBuiltInTrue();
}

interface SkillSummaryProjection {
    Long getSkillId();

    String getCode();

    String getName();

    String getSummary();

    Long getVersionId();

    Integer getVersion();

    Boolean getBuiltIn();
}

@Repository
interface SkillCategoryRepository
        extends JpaRepository<com.xuejiai.aaf.framework.engine.skill.SkillCategory, Long> {
    List<com.xuejiai.aaf.framework.engine.skill.SkillCategory> findByCodeIn(Set<String> codes);
}

@Repository
interface SkillVersionRepository extends JpaRepository<SkillVersion, Long> {
    Optional<SkillVersion> findByIdAndStatus(Long id, String status);

    Optional<SkillVersion> findByIdAndSkillIdAndStatus(Long id, Long skillId, String status);

    Optional<SkillVersion> findFirstBySkillIdOrderByVersionDesc(Long skillId);

    List<SkillVersion> findBySkillIdOrderByVersionDesc(Long skillId);
}

@Repository
interface SkillToolRequirementRepository extends JpaRepository<SkillToolRequirement, Long> {
    List<SkillToolRequirement> findBySkillVersionId(Long skillVersionId);

    List<SkillToolRequirement> findBySkillVersionIdOrderBySortOrderAscIdAsc(Long skillVersionId);
}

@Repository
interface SkillModelRequirementRepository extends JpaRepository<SkillModelRequirement, Long> {
    List<SkillModelRequirement> findBySkillVersionId(Long skillVersionId);

    List<SkillModelRequirement> findBySkillVersionIdOrderByCapabilityAsc(Long skillVersionId);
}
