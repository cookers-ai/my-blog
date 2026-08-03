package com.miniblog.service.Impl;

import com.miniblog.mapper.AdminMapper;
import com.miniblog.service.AdminService;
import com.miniblog.utils.RedisConstants;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class AdminServiceImpl implements AdminService {
    @Autowired
    private AdminMapper adminMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    //更新用户状态(封禁/解封)
    @Override
    public void updateUserStatus(Integer id, Integer userStatus) {
        adminMapper.updateUserStatus(id, userStatus);
    }

    //批量更新用户状态(封禁/解封)
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserStatuslist(List<Integer> ids, Integer userStatus) {
        adminMapper.updateUserStatuslist(ids, userStatus);
    }

    //管理员删除指定文章
    @Override
    public void adminDeleteArticle(Integer id) {
        adminMapper.adminDeleteArticle(id);
    }

    //更新文章状态(下架/发布)
    @Override
    public void updateArticleState(Integer id, String state) {
        adminMapper.updateArticleState(id, state);
    }

    //批量删除评论
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(List<Long> ids) {
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("评论id列表不能为空");
        }
        // 统计每篇文章下待删除的评论数
        Map<Long, Map<String, Object>> countMap = adminMapper.countComment(ids);
        if (countMap == null || countMap.isEmpty()) {
            return;
        }
        // 删除评论
        adminMapper.deleteComments(ids);
        // 更新每篇文章的评论数 + 清理 Redis 计数缓存
        for (Map.Entry<Long, Map<String, Object>> entry : countMap.entrySet()) {
            Long articleId = entry.getKey();
            Integer count = ((Number) entry.getValue().get("count")).intValue();
            // 更新 MySQL
            adminMapper.updateArticleCommentCount(articleId, count);
            // 清理 Redis 计数缓存（用 HINCRBY 保留 viewCount 不丢失）
            String countKey = RedisConstants.ARTICLE_DETAIL_COUNT_KEY + articleId;
            Boolean exists = stringRedisTemplate.hasKey(countKey);
            if (Boolean.TRUE.equals(exists)) {
                stringRedisTemplate.opsForHash().increment(countKey, "commentCount", -count);
            }
        }
    }
}
