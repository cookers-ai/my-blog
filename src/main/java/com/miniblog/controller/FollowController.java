package com.miniblog.controller;

import com.miniblog.pojo.PageBean;
import com.miniblog.pojo.Result;
import com.miniblog.pojo.User;
import com.miniblog.service.FollowSevice;
import com.miniblog.utils.RedisConstants;
import com.miniblog.utils.ThreadLocalUtil;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/follow")
public class FollowController {
    @Autowired
    private FollowSevice followSevice;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @PostMapping("/{followeeId}")
    public Result followUser(@PathVariable Integer followeeId) {
        Map<String, Object> map = ThreadLocalUtil.get();
        Integer userId = (Integer) map.get("id");
        String key = RedisConstants.FOLLOW_LIST_KEY + userId;
        boolean isFollow = followSevice.isFollow(userId, followeeId);
        if (isFollow) {
            return Result.error("您已关注该用户");
        }
        if (userId.equals(followeeId)) {
            return Result.error("不能关注自己");
        }
        followSevice.followUser(userId, followeeId);
        stringRedisTemplate.opsForZSet()
                .add(key, followeeId.toString(), System.currentTimeMillis());
        return Result.success("关注成功");
    }

    @PostMapping("/cancel/{followeeId}")
    public Result cancelFollowUser(@PathVariable Integer followeeId) {
        Map<String, Object> map = ThreadLocalUtil.get();
        Integer userId = (Integer) map.get("id");
        String key = RedisConstants.FOLLOW_LIST_KEY + userId;
        boolean isFollow = followSevice.isFollow(userId, followeeId);
        if (!isFollow) {
            return Result.error("您未关注该用户");
        }
        followSevice.cancelFollowUser(userId, followeeId);
        stringRedisTemplate.opsForZSet().remove(key, followeeId.toString());
        return Result.success("取关成功");
    }

    @GetMapping("/isFollow/{followeeId}")
    public Result<Boolean> isFollow(@PathVariable Integer followeeId) {
        Map<String, Object> map = ThreadLocalUtil.get();
        Integer userId = (Integer) map.get("id");
        String key = RedisConstants.FOLLOW_LIST_KEY + userId;
        Double score = stringRedisTemplate.opsForZSet().score(key, followeeId.toString());
        if (score != null) {
            return Result.success(true);
        }
        boolean isFollow = followSevice.isFollow(userId, followeeId);
        return Result.success(isFollow);
    }

    @GetMapping("/followList")
    public Result<PageBean<User>> followList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Map<String, Object> map = ThreadLocalUtil.get();
        Integer userId = (Integer) map.get("id");
        PageBean<User> pb = followSevice.followList(userId, pageNum, pageSize);
        return Result.success(pb);
    } 
    @GetMapping("/followerList")
    public Result<PageBean<User>> followerList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Map<String, Object> map = ThreadLocalUtil.get();
        Integer userId = (Integer) map.get("id");
        PageBean<User> pb = followSevice.followerList(userId, pageNum, pageSize);
        return Result.success(pb);
    }
    }
