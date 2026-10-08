package com.xuejiai.aaf.module.digitalhuman.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import com.xuejiai.aaf.module.digitalhuman.domain.CreditConsumption;
import com.xuejiai.aaf.module.digitalhuman.vo.CreditConsumptionVO;

/** 数字人积分消耗记录对象转换器。 */
@Mapper
public interface CreditConsumptionConvert {

    CreditConsumptionConvert INSTANCE = Mappers.getMapper(CreditConsumptionConvert.class);

    CreditConsumptionVO toVO(CreditConsumption entity);
}
