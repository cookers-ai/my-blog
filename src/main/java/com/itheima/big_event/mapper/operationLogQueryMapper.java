package com.itheima.big_event.mapper;

import com.itheima.big_event.pojo.OperationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface operationLogQueryMapper {
    //根据用户名和模块查询日志
    List<OperationLog> selectByUserNameAndModule(@Param("username") String username, @Param("module") String module);
}
