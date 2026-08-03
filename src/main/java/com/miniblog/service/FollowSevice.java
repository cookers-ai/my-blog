package com.miniblog.service;

import com.miniblog.pojo.PageBean;
import com.miniblog.pojo.User;

public interface FollowSevice {
    //检查用户是否关注该用户
    boolean isFollow(Integer userId, Integer followeeId);
    //关注用户
    void followUser(Integer userId, Integer followeeId);
    //取关用户
    void cancelFollowUser(Integer userId, Integer followeeId);
    //查询关注列表（分页）
    PageBean<User> followList(Integer userId, Integer pageNum, Integer pageSize);
    //查询粉丝列表（分页）
    PageBean<User> followerList(Integer userId, Integer pageNum, Integer pageSize);
}
