package com.xuejiai.aaf.framework.crud;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.xuejiai.aaf.common.model.BaseEntity;
import com.xuejiai.aaf.common.model.PageParam;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * BaseCrudService 乐观锁行为单元测试。
 *
 * <p>验证 update 时 Hibernate 抛出 {@link ObjectOptimisticLockingFailureException}（version 不匹配）能正确传播。
 *
 * @author Kiro
 */
class BaseCrudServiceOptimisticLockTest extends BaseMockitoUnitTest {

    // ===== 测试用桩实体 =====

    @Getter
    @Setter
    @Entity
    @Table(name = "stub_entity")
    static class StubEntity extends BaseEntity {
        @Column(name = "name")
        private String name;
    }

    record StubVO(Long id, String name, Integer version) {}

    record StubCreateDTO(String name) {}

    record StubUpdateDTO(String name) {}

    static class StubPageParam extends PageParam {}

    // ===== 测试用 Repository =====

    interface StubRepository
            extends JpaRepository<StubEntity, Long>, JpaSpecificationExecutor<StubEntity> {}

    // ===== 被测 Service =====

    static class StubCrudService
            extends BaseCrudService<
                    StubEntity, StubVO, StubCreateDTO, StubUpdateDTO, StubPageParam> {

        private final StubRepository repo;

        StubCrudService(StubRepository repo) {
            this.repo = repo;
        }

        @Override
        protected JpaRepository<StubEntity, Long> getRepository() {
            return repo;
        }

        @Override
        protected JpaSpecificationExecutor<StubEntity> getSpecExecutor() {
            return repo;
        }

        @Override
        protected StubVO toVO(StubEntity e) {
            return new StubVO(e.getId(), e.getName(), e.getVersion());
        }

        @Override
        protected StubEntity toEntity(StubCreateDTO dto) {
            var e = new StubEntity();
            e.setName(dto.name());
            return e;
        }

        @Override
        protected void updateEntity(StubEntity e, StubUpdateDTO dto) {
            e.setName(dto.name());
        }
    }

    // ===== 测试 =====

    @Mock private StubRepository repo;

    @Test
    @DisplayName("Given version 不匹配 When update Then 抛出 ObjectOptimisticLockingFailureException")
    void should_throw_optimistic_lock_exception_when_version_conflict() {
        var service = new StubCrudService(repo);

        var entity = new StubEntity();
        entity.setId(1L);
        entity.setName("旧名称");
        entity.setVersion(1);

        when(repo.findOne(any(Specification.class))).thenReturn(Optional.of(entity));
        // 模拟 Hibernate 检测到 version 冲突时抛出异常
        when(repo.save(any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(StubEntity.class, 1L));

        assertThatThrownBy(() -> service.update(1L, new StubUpdateDTO("新名称")))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("Given version 匹配 When update Then 保存成功并返回更新后的 VO")
    void should_update_successfully_when_version_matches() {
        var service = new StubCrudService(repo);

        var entity = new StubEntity();
        entity.setId(1L);
        entity.setName("旧名称");
        entity.setVersion(1);

        when(repo.findOne(any(Specification.class))).thenReturn(Optional.of(entity));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var vo = service.update(1L, new StubUpdateDTO("新名称"));

        verify(repo).save(entity);
        org.assertj.core.api.Assertions.assertThat(vo.name()).isEqualTo("新名称");
    }
}
