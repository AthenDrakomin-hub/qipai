package com.poker.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.poker.platform.entity.GameRoom;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GameRoomMapper extends BaseMapper<GameRoom> {
}
