package com.miniblog.service.Impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.miniblog.mapper.FollowMapper;
import com.miniblog.mapper.UserMapper;
import com.miniblog.pojo.Follow;
import com.miniblog.pojo.PageBean;
import com.miniblog.pojo.User;
import com.miniblog.service.FollowSevice;
import com.miniblog.utils.RedisConstants;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class FollowServiceImpl implements FollowSevice {
    @Autowired
    private FollowMapper followMapper;
    @Autowired
    private UserMapper userMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    //判断用户是否关注该用户
    @Override
    public boolean isFollow(Integer userId, Integer followeeId) {
        int count = followMapper.isFollow(userId, followeeId);
        return count > 0;
    }

    //关注用户
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void followUser(Integer userId, Integer followeeId) {
        Follow follow = new Follow();
        follow.setFollowerId(userId);
        follow.setFolloweeId(followeeId);
        follow.setCreateTime(LocalDateTime.now());
        followMapper.insert(follow);
        // 关注者：关注数+1
        followMapper.updateFollowCount(userId, 1);
        // 被关注者：粉丝数+1
        followMapper.updateFansCount(followeeId, 1);
    }

    //取关用户
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelFollowUser(Integer userId, Integer followeeId) {
        followMapper.deleteByUserAndFollowee(userId, followeeId);
        // 关注者：关注数-1
        followMapper.updateFollowCount(userId, -1);
        // 被关注者：粉丝数-1
        followMapper.updateFansCount(followeeId, -1);
    }

    //查询关注列表（分页）— Redis ZSet 缓存优先 + DB 兜底
    @Override
    public PageBean<User> followList(Integer userId, Integer pageNum, Integer pageSize) {
        String key = RedisConstants.FOLLOW_LIST_KEY + userId;
        Long total = stringRedisTemplate.opsForZSet().size(key);

        // ① Redis ZSet 缓存命中 → 从 Redis 分页取 ID，再批量查用户信息
        if (total != null && total > 0) {
            int start = (pageNum - 1) * pageSize;
            int end = start + pageSize - 1;
            Set<String> idSet = stringRedisTemplate.opsForZSet()
                    .reverseRange(key, start, end);
            if (idSet == null || idSet.isEmpty()) {
                return new PageBean<>();
            }

            List<Integer> ids = idSet.stream()
                    .map(Integer::parseInt)
                    .collect(Collectors.toList());

            // 批量查用户
            List<User> users = userMapper.selectBatchIds(ids);

            // 保持 ZSet 中的顺序（ZREVRANGE 返回的就是按 score 倒序）
            Map<Integer, User> userMap = new HashMap<>();
            for (User user : users) {
                userMap.put(user.getId(), user);
            }
            List<User> ordered = new ArrayList<>();
            for (Integer id : ids) {
                User u = userMap.get(id);
                if (u != null) {
                    ordered.add(u);
                }
            }

            PageBean<User> pb = new PageBean<>();
            pb.setTotal(total);
            pb.setItems(ordered);
            return pb;
        }

        // ② Redis 未命中 → 从 MySQL 全量加载关注列表到 Redis，再分页查
        List<Follow> allFollows = followMapper.findFollowees(userId);
        if (allFollows == null || allFollows.isEmpty()) {
            return new PageBean<>();
        }
        // 预热 Redis ZSet
        for (Follow f : allFollows) {
            // score 用关注时间戳；如果 create_time 为 null，用当前时间
            long score = f.getCreateTime() != null
                    ? f.getCreateTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                    : System.currentTimeMillis();
            stringRedisTemplate.opsForZSet()
                    .add(key, f.getFolloweeId().toString(), score);
        }
        // 设置过期时间，防止 ZSet 永久占用内存
        stringRedisTemplate.expire(key, 30, TimeUnit.MINUTES);

        // 递归调自己，走 Redis 路径
        return followList(userId, pageNum, pageSize);
    }

    @Override
    public PageBean<User> followerList(Integer userId, Integer pageNum, Integer pageSize) {
        PageBean<User> pb = new PageBean<>();
        PageHelper.startPage(pageNum, pageSize);
        List<User> list = followMapper.selectFollowerUsers(userId);
        Page<User> page = (Page<User>) list;
        pb.setTotal(page.getTotal());
        pb.setItems(page.getResult());
        return pb;
    }
}
