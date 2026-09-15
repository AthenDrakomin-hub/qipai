package com.poker.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.poker.platform.entity.GameRound;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GameRoundMapper extends BaseMapper<GameRound> {
}
