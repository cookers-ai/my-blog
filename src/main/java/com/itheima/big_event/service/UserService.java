package com.itheima.big_event.service;

import com.itheima.big_event.pojo.PageBean;
import com.itheima.big_event.pojo.User;

import java.util.List;

public interface UserService {
    //根据用户名查询用户
    User findByUserName(String userName);
    //注册用户
    void register(String userName, String password);
    //更新用户信息
    void update(User user);
    //更新用户头像
    void updataAvatar(String avatar);
    //更新用户密码
    void updatePassword(String newPassword);
    //校验密码
    boolean checkPassword(String rawPassword, String encodedPassword);
    //查询所有用户
    PageBean<User> findAll(Integer pageNum, Integer pageSize);
    //根据用户id查询用户信息
    User findById(Integer userId);
}
