package com.xuejiai.aaf.module.ai.aigc.work.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.xuejiai.aaf.module.ai.aigc.work.domain.AigcWork;
import com.xuejiai.aaf.module.ai.aigc.work.domain.AigcWorkPublication;
import com.xuejiai.aaf.module.ai.aigc.work.repository.AigcWorkPublicationRepository;
import com.xuejiai.aaf.module.ai.aigc.work.repository.AigcWorkRepository;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class AigcWorkCompletionEvidenceAdapterTest extends BaseMockitoUnitTest {

    @Mock private AigcWorkRepository workRepository;
    @Mock private AigcWorkPublicationRepository publicationRepository;
    @InjectMocks private AigcWorkCompletionEvidenceAdapter adapter;

    @Test
    @DisplayName("Given 活动发布与成功发布并存 When 加载完成证据 Then 统计阻塞数并去重成功渠道")
    void should_report_active_publications_and_distinct_succeeded_channels() {
        // 准备参数
        var work = new AigcWork();
        work.setId(11L);
        var pending = publication(21L, "PENDING", 101L);
        var succeeded = publication(22L, "SUCCEEDED", 102L);
        var duplicateChannel = publication(23L, "SUCCEEDED", 102L);
        when(workRepository.findByProjectIdAndStatusNot(7L, "ARCHIVED"))
                .thenReturn(List.of(work));
        when(publicationRepository.findByWorkIdIn(List.of(11L)))
                .thenReturn(List.of(pending, succeeded, duplicateChannel));

        // 调用
        var evidence = adapter.load(7L);

        // 断言
        assertThat(evidence.activeWorkCount()).isEqualTo(1);
        assertThat(evidence.publicationCount()).isEqualTo(3);
        assertThat(evidence.activePublicationCount()).isEqualTo(1);
        assertThat(evidence.succeededChannelSpecVersionIds()).containsExactly(102L);
    }

    private AigcWorkPublication publication(Long id, String status, Long channelVersionId) {
        var publication = new AigcWorkPublication();
        publication.setId(id);
        publication.setStatus(status);
        publication.setChannelSpecVersionId(channelVersionId);
        return publication;
    }
}
