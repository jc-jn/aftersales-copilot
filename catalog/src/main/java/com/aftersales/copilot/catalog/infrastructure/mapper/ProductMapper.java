package com.aftersales.copilot.catalog.infrastructure.mapper;

import com.aftersales.copilot.catalog.infrastructure.po.ProductPo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductMapper extends BaseMapper<ProductPo> {
}
