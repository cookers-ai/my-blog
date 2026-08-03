package com.miniblog.mapper;

import com.miniblog.pojo.OperationLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OperationLogMapper {
    @Insert("insert into operation_log(user_id, username, module, operation, method, params, ip, status, error_msg, cost_time, create_time) " +
            "values(#{userId}, #{username}, #{module}, #{operation}, #{method}, #{params}, #{ip}, #{status}, #{errorMsg}, #{costTime}, #{createTime})")
    void insert(OperationLog log);
}
