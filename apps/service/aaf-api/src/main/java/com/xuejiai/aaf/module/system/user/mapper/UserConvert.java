package com.xuejiai.aaf.module.system.user.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.factory.Mappers;

import com.xuejiai.aaf.module.system.user.domain.User;
import com.xuejiai.aaf.module.system.user.vo.UserSimpleVO;
import com.xuejiai.aaf.module.system.user.vo.UserUpdateDTO;
import com.xuejiai.aaf.module.system.user.vo.UserVO;

/** 用户对象转换器。 */
@Mapper
/**
 * @author AaronZZH & Kiro
 */
public interface UserConvert {

    UserConvert INSTANCE = Mappers.getMapper(UserConvert.class);

    UserVO toVO(User user);

    UserSimpleVO toSimpleVO(User user);

    /** 将 DTO 中非 null 字段更新到已有 User 实体。 */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateFromDTO(UserUpdateDTO dto, @MappingTarget User user);
}
