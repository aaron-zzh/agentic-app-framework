package com.xuejiai.aaf.module.ai.aigc.task.mapper;

import org.mapstruct.Mapper;

import com.xuejiai.aaf.module.ai.aigc.task.domain.AigcTask;
import com.xuejiai.aaf.module.ai.aigc.task.vo.AigcTaskVO;

/** AIGC 任务对象转换器。 */
@Mapper(componentModel = "spring")
public interface AigcTaskMapper {

    AigcTaskVO toVO(AigcTask task);
}
