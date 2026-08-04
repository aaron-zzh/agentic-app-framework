package com.xuejiai.aaf.module.ai.aigc.configuration.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import com.xuejiai.aaf.module.ai.aigc.configuration.domain.AigcChannelSpec;
import com.xuejiai.aaf.module.ai.aigc.configuration.vo.AigcChannelSpecCreateDTO;

/**
 * 渠道规格对象转换器。
 *
 * @author AaronZZH & Kiro
 */
@Mapper
public interface AigcChannelSpecConvert {

    AigcChannelSpecConvert INSTANCE = Mappers.getMapper(AigcChannelSpecConvert.class);

    AigcChannelSpec toEntity(AigcChannelSpecCreateDTO dto);
}
