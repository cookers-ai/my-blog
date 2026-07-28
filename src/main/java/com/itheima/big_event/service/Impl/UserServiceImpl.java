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
        return userMapper.findByUserName(userName);
    }

    @Override
    public void register(String userName, String password) {
        User user = new User();
        user.setUsername(userName);
        user.setPassword(encoder.encode(password));
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        user.setRole("user");
        user.setUserStatus(0);
        userMapper.add(user);
        User newUser = userMapper.findByUserName(userName);
        if (newUser == null) {
            throw new RuntimeException("用户注册失败，无法获取用户ID");
        }
    }

    @Override
    public void update(User user) {
        user.setUpdateTime(LocalDateTime.now());
        userMapper.update(user);
    }

    @Override
    public void updataAvatar(String avatar) {
        Map<String, Object> map = ThreadLocalUtil.get();
        Integer id = (Integer) map.get("id");
        userMapper.updateAvatar(avatar, id);
    }

    @Override
    public void updatePassword(String newPassword) {
        Map<String, Object> map = ThreadLocalUtil.get();
        Integer id = (Integer) map.get("id");
        userMapper.updatePassword(encoder.encode(newPassword), id);
    }

    @Override
    public PageBean<User> findAll(Integer pageNum, Integer pageSize) {
        PageBean<User> ppb = new PageBean<>();
        PageHelper.startPage(pageNum, pageSize);
        List<User> list = userMapper.findAll(pageNum, pageSize);
        Page<User> page = (Page<User>) list;
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
