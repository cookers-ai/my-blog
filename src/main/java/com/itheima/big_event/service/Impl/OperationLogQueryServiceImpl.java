package com.itheima.big_event.service.Impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.itheima.big_event.mapper.operationLogQueryMapper;
import com.itheima.big_event.pojo.OperationLog;
import com.itheima.big_event.pojo.PageBean;
import com.itheima.big_event.service.OperationLogQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OperationLogQueryServiceImpl implements OperationLogQueryService {
    @Autowired
    private operationLogQueryMapper operationLogMapper;
    @Override
    public PageBean<OperationLog> queryLog(Integer pageNum, Integer pageSize, String username, String module) {
       //创建分页查询对象
       PageBean<OperationLog> pageBean = new PageBean<>();
       //开启分页查询
       PageHelper.startPage(pageNum, pageSize);
       //根据用户名和模块查询日志
       List<OperationLog> logs = operationLogMapper.selectByUserNameAndModule(username, module);
       //将List对象强转为Page对象
       Page<OperationLog> page = (Page<OperationLog>) logs;
       //返回分页结果
       pageBean.setTotal(page.getTotal());
       pageBean.setItems(page.getResult());
       return pageBean;
    }
}
