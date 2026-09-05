package com.xuejiai.aaf.module.ai.aigc.work.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.context.ApplicationEventPublisher;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.aigc.configuration.api.AigcChannelSpecApi;
import com.xuejiai.aaf.module.ai.aigc.event.service.AigcActivityEventService;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectApi;
import com.xuejiai.aaf.module.ai.aigc.work.domain.AigcWorkPublication;
import com.xuejiai.aaf.module.ai.aigc.work.repository.AigcWorkPublicationRepository;
import com.xuejiai.aaf.module.ai.aigc.work.repository.AigcWorkRepository;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class AigcWorkServiceTest extends BaseMockitoUnitTest {

    @Mock private AigcWorkRepository repository;
    @Mock private AigcWorkPublicationRepository publicationRepository;
    @Mock private AigcProjectApi projectApi;
    @Mock private AigcChannelSpecApi channelSpecApi;
    @Mock private OperatorContext operatorContext;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private AigcActivityEventService activityEventService;
    @InjectMocks private AigcWorkService service;

    @Test
    @DisplayName("Given Publication 版本已推进 When 按旧版本变更 Then CAS 拒绝")
    void should_reject_stale_publication_version() {
        // 准备参数
        var publication = publication("FAILED", 3);

        // 调用 + 断言
        assertThatThrownBy(() -> service.requirePublicationVersion(publication, 2))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Publication 已被其他操作更新");
    }

    @Test
    @DisplayName("Given Work 存在活动 Publication When 判断归档前置 Then 阻断归档")
    void should_block_archive_when_active_publication_exists() {
        // 准备参数
        var pending = publication("PENDING", 1);
        var succeeded = publication("SUCCEEDED", 2);

        // 调用
        var active = AigcWorkService.hasActivePublication(List.of(succeeded, pending));

        // 断言
        assertThat(active).isTrue();
    }

    private AigcWorkPublication publication(String status, int version) {
        var publication = new AigcWorkPublication();
        publication.setStatus(status);
        publication.setVersion(version);
        return publication;
    }
}
