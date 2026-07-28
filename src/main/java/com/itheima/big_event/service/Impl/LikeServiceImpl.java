package com.itheima.big_event.service.Impl;

import com.itheima.big_event.mapper.ArticleMapper;
import com.itheima.big_event.mapper.LikeMapper;
import com.itheima.big_event.service.LikeService;
import com.itheima.big_event.utils.RedisConstants;
import com.itheima.big_event.utils.ThreadLocalUtil;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;

@Service
public class LikeServiceImpl implements LikeService {
    @Autowired
    private LikeMapper likeMapper;
    @Autowired
    private ArticleMapper articleMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void likeArticle(Integer articleId) {
        Map<String, Object> map = ThreadLocalUtil.get();
        Integer userId = (Integer) map.get("id");
        final boolean[] isLike = new boolean[1];

        try {
            // 尝试插入 UNIQUE 约束保证并发下只有一个能成功
            likeMapper.addByUserIdAndArticleId(userId, articleId);
            articleMapper.incrLikeCount(articleId);
            isLike[0] = true;
        } catch (DuplicateKeyException e) {
            // INSERT 冲突 = 已经点过赞 ， 取消点赞（
            likeMapper.deleteByUserIdAndArticleId(userId, articleId);
            articleMapper.decrLikeCount(articleId);
            isLike[0] = false;
        }

        String countKey = RedisConstants.ARTICLE_DETAIL_COUNT_KEY + articleId;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
            @Override
            public void afterCommit() {
                Boolean exists = stringRedisTemplate.hasKey(countKey);
                if (Boolean.TRUE.equals(exists)) {
                    int delta = isLike[0] ? 1 : -1;
                    stringRedisTemplate.opsForHash().increment(countKey, "likeCount", delta);
                }
            }
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    //是否点赞文章
      public boolean isLikeArticle(Integer articleId) {
        //获取id
        Map<String, Object> map = ThreadLocalUtil.get();
        Integer userId = (Integer) map.get("id");
        //检查是否点赞
        int count = likeMapper.selectByUserIdAndArticleId(userId, articleId);
        return count > 0;
    }
}
