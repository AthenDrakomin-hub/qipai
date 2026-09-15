package com.poker.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.poker.platform.entity.UserMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMessageMapper extends BaseMapper<UserMessage> {
}
