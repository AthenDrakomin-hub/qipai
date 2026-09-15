package com.poker.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.poker.platform.entity.WithdrawOrder;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WithdrawOrderMapper extends BaseMapper<WithdrawOrder> {
}
