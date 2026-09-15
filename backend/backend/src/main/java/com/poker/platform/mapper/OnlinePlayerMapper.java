package com.poker.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.poker.platform.entity.OnlinePlayer;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OnlinePlayerMapper extends BaseMapper<OnlinePlayer> {
}
