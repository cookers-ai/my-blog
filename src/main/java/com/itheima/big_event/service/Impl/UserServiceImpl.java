package com.itheima.big_event.service.Impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.itheima.big_event.mapper.UserMapper;
import com.itheima.big_event.pojo.PageBean;
import com.itheima.big_event.pojo.User;
import com.itheima.big_event.service.UserService;
import com.itheima.big_event.utils.ThreadLocalUtil;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;


@Service
public class UserServiceImpl implements UserService {
    private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();


    @Autowired
    private UserMapper userMapper;
    @Override
    public User findByUserName(String userName) {
        //调用UserMapper的方法
        return userMapper.findByUserName(userName);
    }
    //注册用户
    @Override
    public void register(String userName, String password) {
        //先加密密码

        //调用UserMapper的方法
       User user=new User();
        user.setUsername(userName);
        user.setPassword(encoder.encode(password));
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        user.setRole("user");
        user.setUserStatus(0);
        userMapper.add(user);
        User newUser = userMapper.findByUserName(userName);
        if (newUser == null ) {
            throw new RuntimeException("用户注册失败，无法获取用户ID");
        }
    }


    @Override
    public void update(User user) {
        //更新更新时间
        user.setUpdateTime(LocalDateTime.now());
        //调用UserMapper的方法
        userMapper.update(user);
    }
//更新用户头像
    @Override
    public void updataAvatar(String avatar) {
        //从ThreadLocal中获取用户数据
        Map<String,Object> map = ThreadLocalUtil.get();
        //从map中获取用户id
       Integer id = (Integer) map.get("id");
        //调用UserMapper的方法
        userMapper.updateAvatar(avatar,id);
    }

    @Override
    public void updatePassword( String newPassword) {
        //从ThreadLocal中获取用户数据
        Map<String,Object> map = ThreadLocalUtil.get();
        //从map中获取用户id
        Integer id = (Integer) map.get("id");
        //需要对新密码进行加密
      userMapper.updatePassword(encoder.encode(newPassword),id);
       }
//查询所有用户
    @Override
    public PageBean<User> findAll(Integer pageNum, Integer pageSize) {
        //创建pageBean对象用来封装查询好的数据
        PageBean<User> ppb = new PageBean<>();
        //开启分页查询(pageHelper)
        //返回List对象
        PageHelper.startPage(pageNum,pageSize);
        //调用UserMapper的方法
        List<User> list = userMapper.findAll(pageNum, pageSize);
        //将List对象强转为Page对象
        Page<User> page = (Page<User>) list;
        //将数据填充到PageBean对象中
        ppb.setTotal(page.getTotal());
        ppb.setItems(page.getResult());
        return ppb;
    }

    @Override
    public User findById(Integer userId) {

        return userMapper.findById(userId);
    }

    @Override
    public boolean checkPassword(String rawPassword, String encodedPassword) {
        return encoder.matches(rawPassword, encodedPassword);
    }
}

