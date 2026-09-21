package com.aftersales.copilot.order.infrastructure.mapper;

import com.aftersales.copilot.order.infrastructure.po.PaymentRecordPo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PaymentRecordMapper extends BaseMapper<PaymentRecordPo> {
}
