package com.miniblog.service.Impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.miniblog.mapper.ArticleMapper;
import com.miniblog.mapper.CommentMapper;
import com.miniblog.pojo.Comment;
import com.miniblog.pojo.PageBean;
import com.miniblog.service.CommentService;
import com.miniblog.utils.RedisConstants;
import com.miniblog.utils.ThreadLocalUtil;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;


@Service
public class CommentServiceImpl implements CommentService {
    @Autowired
    private CommentMapper commentMapper;
    @Autowired
    private ArticleMapper articleMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    //添加评论
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addComment(Comment comment) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer userId = (Integer) claims.get("id");
        comment.setUserId(userId);
        // 添加评论
        commentMapper.add(comment);
        // 增加文章的评论数
        articleMapper.incrCommentCount(comment.getArticleId());
        String countKey = RedisConstants.ARTICLE_DETAIL_COUNT_KEY+comment.getArticleId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
            @Override
            public void afterCommit() {
                boolean exists = stringRedisTemplate.hasKey(countKey);
                if(exists){
                    // 评论添加成功后增加计数缓存
                    stringRedisTemplate.opsForHash().increment(countKey,"commentCount",1);
                }


            }
        });
    }

    @Override
    public PageBean<Comment> commentList(Integer articleId, Integer pageNum, Integer pageSize) {
        PageBean<Comment> pb = new PageBean<>();
        PageHelper.startPage(pageNum, pageSize);
        List<Comment> list = commentMapper.findCommentList(articleId);
        Page<Comment> page = (Page<Comment>) list;
        pb.setTotal(page.getTotal());
        pb.setItems(page.getResult());
        return pb;
    }
    //删除评论
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(Long id) {
        Map<String, Object> claims = ThreadLocalUtil.get();
        Integer userId = (Integer) claims.get("id");
        String role = (String) claims.get("role");
        // 查询评论
        Comment comment = commentMapper.findById(id);
        if (comment == null) {
            throw new RuntimeException("评论不存在");
        }
        // 只有评论作者本人或管理员可以删除
        if (!"admin".equals(role) && !comment.getUserId().equals(userId)) {
            throw new RuntimeException("您没有权限删除该评论");
        }
        // 删除评论
        int rows = commentMapper.deleteComment(id, userId);
        if (rows == 0) {
            return; // 评论已被其他人删除（并发）跳过扣减计数
        }
        // 减少文章的评论数
        articleMapper.decrCommentCount(comment.getArticleId());
        String countKey = RedisConstants.ARTICLE_DETAIL_COUNT_KEY+comment.getArticleId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
            @Override
            public void afterCommit() {
                // 评论删除成功后删除缓存
                boolean exists = stringRedisTemplate.hasKey(countKey);
                if(exists){
                    stringRedisTemplate.opsForHash().increment(countKey,"commentCount",-1);
                }
            }
        });

    }
}
