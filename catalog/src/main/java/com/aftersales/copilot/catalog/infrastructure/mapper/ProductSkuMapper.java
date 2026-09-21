package com.aftersales.copilot.catalog.infrastructure.mapper;

import com.aftersales.copilot.catalog.infrastructure.po.ProductSkuPo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductSkuMapper extends BaseMapper<ProductSkuPo> {
}
