package com.itheima.big_event.controller;

import com.itheima.big_event.pojo.PageBean;
import com.itheima.big_event.pojo.Result;
import com.itheima.big_event.pojo.User;
import com.itheima.big_event.service.FollowSevice;
import com.itheima.big_event.utils.RedisConstants;
import com.itheima.big_event.utils.ThreadLocalUtil;
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

    /*
     * 关注用户
     */
    @PostMapping("/{followeeId}")
    public Result followUser(@PathVariable Integer followeeId) {
        Map<String, Object> map = ThreadLocalUtil.get();
        Integer userId = (Integer) map.get("id");
        String key = RedisConstants.FOLLOW_LIST_KEY + userId;
        //检查用户是否已经关注该用户
        boolean isFollow = followSevice.isFollow(userId, followeeId);
        if (isFollow) {
            return Result.error("您已关注该用户");
        }
        //检查用户是否关注自己
        if (userId.equals(followeeId)) {
            return Result.error("不能关注自己");
        }
        //关注用户
        followSevice.followUser(userId, followeeId);
        //更新 Redis ZSet（score = 关注时间戳）
        stringRedisTemplate.opsForZSet()
                .add(key, followeeId.toString(), System.currentTimeMillis());
        return Result.success("关注成功");
    }

    /*
     * 取关用户
     */
    @PostMapping("/cancel/{followeeId}")
    public Result cancelFollowUser(@PathVariable Integer followeeId) {
        Map<String, Object> map = ThreadLocalUtil.get();
        Integer userId = (Integer) map.get("id");
        String key = RedisConstants.FOLLOW_LIST_KEY + userId;
        //检查用户是否关注该用户
        boolean isFollow = followSevice.isFollow(userId, followeeId);
        if (!isFollow) {
            return Result.error("您未关注该用户");
        }
        //取关用户
        followSevice.cancelFollowUser(userId, followeeId);
        //从 Redis ZSet 移除
        stringRedisTemplate.opsForZSet().remove(key, followeeId.toString());
        return Result.success("取关成功");
    }

    /*
     * 查询是否关注
     * @param followeeId 被关注用户id
     */
    @GetMapping("/isFollow/{followeeId}")
    public Result<Boolean> isFollow(@PathVariable Integer followeeId) {
        Map<String, Object> map = ThreadLocalUtil.get();
        Integer userId = (Integer) map.get("id");
        String key = RedisConstants.FOLLOW_LIST_KEY + userId;
        //优先从 Redis ZSet 查（O(1)）
        Double score = stringRedisTemplate.opsForZSet().score(key, followeeId.toString());
        if (score != null) {
            return Result.success(true);
        }
        //缓存未命中，查 DB
        boolean isFollow = followSevice.isFollow(userId, followeeId);
        return Result.success(isFollow);
    }

    /*
     * 查询关注列表（分页）
     */
    @GetMapping("/followList")
    public Result<PageBean<User>> followList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Map<String, Object> map = ThreadLocalUtil.get();
        Integer userId = (Integer) map.get("id");
        PageBean<User> pb = followSevice.followList(userId, pageNum, pageSize);
        return Result.success(pb);
    } 
    /*
    * 查询粉丝列表（分页）
    */
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
