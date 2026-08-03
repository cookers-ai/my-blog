package com.miniblog.service.Impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.miniblog.DTO.ArticleCountDTO;
import com.miniblog.DTO.ScrollResultDTO;
import com.miniblog.mapper.ArticleMapper;
import com.miniblog.mapper.FollowMapper;
import com.miniblog.mapper.UserMapper;
import com.miniblog.pojo.*;
import com.miniblog.service.ArticleService;
import com.miniblog.service.LikeService;
import com.miniblog.utils.CacheClient;
import com.miniblog.utils.RedisConstants;
import com.miniblog.utils.ThreadLocalUtil;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class ArticleServiceImpl implements ArticleService {
    @Autowired
    private ArticleMapper articleMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;
    @Autowired
    private FollowMapper followMapper;
    @Autowired
    private LikeService likeService;
    @Autowired
    private UserMapper userMapper;

    @Override
    public Result addArticle(Article article) {
        article.setCreateTime(LocalDateTime.now());
        article.setUpdateTime(LocalDateTime.now());
        Map<String, Object> map = ThreadLocalUtil.get();
        Integer userId = (Integer) map.get("id");
        article.setCreateUser(userId);
        if (article.getCoverImg() == null) {
            article.setCoverImg("");
        }
        boolean isSuccess = articleMapper.add(article);
        if (!isSuccess) {
            return Result.error("添加文章失败");
        }
        deleteHomeListCache();
        List<Follow> fanslist = followMapper.findFollowers(userId);
        for (Follow fan : fanslist) {
            Integer followerId = fan.getFollowerId();
            String key = RedisConstants.FANS_KEY_PREFIX + followerId;
            stringRedisTemplate.opsForZSet()
                    .add(key, article.getId().toString(), System.currentTimeMillis());
            stringRedisTemplate.expire(key, RedisConstants.FANS_INBOX_EXPIRE_TIME, TimeUnit.SECONDS);
        }
        return Result.success();
    }

    @Override
    public PageBean<Article> list(Integer pageNum, Integer pageSize, Integer categoryId, String state) {
        PageBean<Article> ppb = new PageBean<>();
        PageHelper.startPage(pageNum, pageSize);
        Map<String, Object> map = ThreadLocalUtil.get();
        Integer userId = (Integer) map.get("id");
        if (userId == null) {
            throw new IllegalStateException("用户未登录，无法查询文章列表");
        }
        List<Article> list = articleMapper.list(userId, state, categoryId);
        Page<Article> page = (Page<Article>) list;
        ppb.setTotal(page.getTotal());
        ppb.setItems(page.getResult());
        return ppb;
    }

    @Override
    public Article detail(Integer id) {
        return articleMapper.findById(id);
    }

    @Override
    public void updateArticle(Article article) {
        article.setUpdateTime(LocalDateTime.now());
        articleMapper.update(article);
    }

    @Override
    public void deleteArticle(Integer id) {
        Map<String, Object> map = ThreadLocalUtil.get();
        Integer userId = (Integer) map.get("id");
        articleMapper.delete(userId, id);
    }

    @Override
    public PageBean<Article> adminList(Integer pageNum, Integer pageSize, Integer categoryId, String userId, String state) {
        PageBean<Article> ppb = new PageBean<>();
        PageHelper.startPage(pageNum, pageSize);
        List<Article> list = articleMapper.adminList(userId, state, categoryId);
        Page<Article> page = (Page<Article>) list;
        ppb.setTotal(page.getTotal());
        ppb.setItems(page.getResult());
        return ppb;
    }

    @Override
    public PageBean<Article> getHomeList(Integer pageNum, Integer pageSize, Integer categoryId, String sort) {
        Long total = articleMapper.count(categoryId);
        int start = (pageNum - 1) * pageSize;
        List<Article> list = articleMapper.selectByPage(categoryId, sort, start, pageSize);
        if (!list.isEmpty()) {
            Set<Integer> ids = new HashSet<>();
            for (Article a : list) { if (a.getCreateUser() != null) ids.add(a.getCreateUser()); }
            if (!ids.isEmpty()) {
                List<User> users = userMapper.selectBatchIds(new ArrayList<>(ids));
                Map<Integer, User> m = new HashMap<>();
                for (User u : users) m.put(u.getId(), u);
                for (Article a : list) {
                    User u = m.get(a.getCreateUser());
                    if (u != null) { a.setAuthorName(getUserDisplayName(u)); a.setAuthorAvatar(u.getUserPic()); }
                }
            }
        }
        PageBean<Article> ppb = new PageBean<>();
        ppb.setItems(list);
        ppb.setPage((int) Math.ceil((double) total / pageSize));
        return ppb;
    }

    @Override
    public List<ArticleCategory> categoryList() {
        return articleMapper.categoryList();
    }

    @Override
    public void enrichCounts(Article article) {
        ArticleCountDTO countDTO = articleMapper.selectCounts(article.getId());
        if (countDTO == null) {
            return;
        }
        article.setLikeCount(countDTO.getLikeCount());
        article.setCommentCount(countDTO.getCommentCount());
        article.setViewCount(countDTO.getViewCount());
    }

    @Override
    public Article getArticle(Integer id) {
        String keyRedis = RedisConstants.ARTICLE_DETAIL_KEY + id;
        long expireTime = RedisConstants.ARTICLE_DETAIL_EXPIRE_TIME;
        long countExpireTime = 30 * 60;
        String CountRedis = RedisConstants.ARTICLE_DETAIL_COUNT_KEY + id;
        String lockKey = RedisConstants.ARTICLE_DETAIL_LOCK_KEY + id;

        Article article = cacheClient.articleDetailMutexLock(keyRedis,
                id, Article.class,
                this::detail,
                expireTime, TimeUnit.SECONDS);
        if (article == null) {
            return null;
        }
        if (article.getCreateUser() != null) {
            User author = userMapper.findById(article.getCreateUser());
            if (author != null) {
                article.setAuthorName(getUserDisplayName(author));
                article.setAuthorAvatar(author.getUserPic());
            }
        }

        Map<Object, Object> countMap = stringRedisTemplate.opsForHash().entries(CountRedis);
        if (!countMap.isEmpty()) {
            extracted(article, countMap);
        } else {
            boolean locked = false;
            try {
                locked = cacheClient.tryLockWithRetry(lockKey, 5, 100);
                if (locked) {
                    countMap = stringRedisTemplate.opsForHash().entries(CountRedis);
                    if (!countMap.isEmpty()) {
                        extracted(article, countMap);
                    } else {
                        this.enrichCounts(article);
                        stringRedisTemplate.opsForHash().putAll(CountRedis, Map.of(
                                "likeCount", String.valueOf(article.getLikeCount()),
                                "commentCount", String.valueOf(article.getCommentCount()),
                                "viewCount", String.valueOf(article.getViewCount())));
                        Random random = new Random();
                        long expireSeconds = countExpireTime + random.nextInt(360);
                        stringRedisTemplate.expire(CountRedis, expireSeconds, TimeUnit.SECONDS);
                    }
                } else {
                    this.enrichCounts(article);
                }
            } catch (Exception e) {
                throw new RuntimeException("缓存重建被中断", e);
            } finally {
                if (locked) {
                    cacheClient.releaseLock(lockKey);
                }
            }
        }

        stringRedisTemplate.opsForHash().increment(CountRedis, "viewCount", 1);
        return article;
    }

    @Override
    public Result queryBlogOfFollow(long max, Integer offset) {
        Map<String, Object> userInfo = ThreadLocalUtil.get();
        Integer userId = (Integer) userInfo.get("id");
        String key = RedisConstants.FANS_KEY_PREFIX + userId;
        Set<ZSetOperations.TypedTuple<String>> tuples = stringRedisTemplate
                .opsForZSet().reverseRangeByScoreWithScores(key, 0, max, offset, 5);
        if (tuples == null || tuples.isEmpty()) {
            List<Follow> follows = followMapper.findFollowees(userId);
            if (follows == null || follows.isEmpty()) {
                return Result.success(new ScrollResultDTO());
            }

            List<Integer> followeeIds = new ArrayList<>();
            for (Follow f : follows) { followeeIds.add(f.getFolloweeId()); }
            List<Article> dbArticles = articleMapper.selectByUsers(followeeIds);
            if (!dbArticles.isEmpty()) {
                Set<Integer> ids2 = new HashSet<>();
                for (Article a : dbArticles) { if (a.getCreateUser() != null) ids2.add(a.getCreateUser()); }
                if (!ids2.isEmpty()) {
                    List<User> users = userMapper.selectBatchIds(new ArrayList<>(ids2));
                    Map<Integer, User> m = new HashMap<>();
                    for (User u : users) m.put(u.getId(), u);
                    for (Article a : dbArticles) {
                        User u = m.get(a.getCreateUser());
                        if (u != null) {
                            a.setAuthorName(getUserDisplayName(u));
                            a.setAuthorAvatar(u.getUserPic());
                        }
                    }
                }
                for (Article a : dbArticles) {
                    a.setIsLike(likeService.isLikeArticle(a.getId()));
                }

                for (Article a : dbArticles) {
                    if (a.getCreateTime() != null) {
                        long sc = a.getCreateTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                        stringRedisTemplate.opsForZSet().add(key, a.getId().toString(), sc);
                    } else {
                        stringRedisTemplate.opsForZSet().add(key, a.getId().toString(), System.currentTimeMillis());
                    }
                }
                stringRedisTemplate.expire(key, RedisConstants.FANS_INBOX_EXPIRE_TIME, TimeUnit.SECONDS);
            }
            ScrollResultDTO fallback = new ScrollResultDTO();
            fallback.setList(dbArticles);
            fallback.setMinTime(0);
            fallback.setOffset(1);
            return Result.success(fallback);
        }

        List<Long> ids = new ArrayList<>(tuples.size());
        long score = 0;
        int off = 1;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            String articleIds = tuple.getValue();
            ids.add(Long.parseLong(articleIds));
            Long time = tuple.getScore().longValue();
            if (time == score) {
                off++;
            } else {
                score = time;
                off = 1;
            }
        }

        List<Article> articles = articleMapper.selectBatchIds(ids);
        Set<Integer> authorIds = new HashSet<>();
        for (Article article : articles) {
            authorIds.add(article.getCreateUser());
        }

        Map<Integer, User> userMap = new HashMap<>();
        if (!authorIds.isEmpty()) {
            List<User> users = userMapper.selectBatchIds(new ArrayList<>(authorIds));
            for (User user : users) {
                userMap.put(user.getId(), user);
            }
        }

        for (Article article : articles) {
            User author = userMap.get(article.getCreateUser());
            if (author != null) {
                article.setAuthorName(getUserDisplayName(author));
                article.setAuthorAvatar(author.getUserPic());
            }
            article.setIsLike(likeService.isLikeArticle(article.getId()));
        }

        ScrollResultDTO scrollResult = new ScrollResultDTO();
        scrollResult.setList(articles);
        scrollResult.setMinTime(score);
        scrollResult.setOffset(off);
        return Result.success(scrollResult);
    }

    private static void extracted(Article article, Map<Object, Object> countMap) {
        article.setLikeCount(Integer.parseInt(countMap.get("likeCount").toString()));
        article.setCommentCount(Integer.parseInt(countMap.get("commentCount").toString()));
        article.setViewCount(Integer.parseInt(countMap.get("viewCount").toString()));
    }

    /**
     * 获取用户展示名称：优先 nickname，为 null 时回退到 username
     */
    private String getUserDisplayName(User user) {
        return user.getNickname() != null && !user.getNickname().isEmpty()
                ? user.getNickname()
                : user.getUsername();
    }

    @Override
    public PageBean<Article> getUserArticles(Integer userId, Integer pageNum, Integer pageSize) {
        long total = articleMapper.countByUserId(userId);
        int start = (pageNum - 1) * pageSize;
        List<Article> list = articleMapper.selectByUserId(userId, start, pageSize);
        if (!list.isEmpty()) {
            User author = userMapper.findById(userId);
            for (Article a : list) {
                if (author != null) {
                    a.setAuthorName(getUserDisplayName(author));
                    a.setAuthorAvatar(author.getUserPic());
                }
            }
        }
        PageBean<Article> pb = new PageBean<>();
        pb.setTotal(total);
        pb.setItems(list);
        pb.setPage((int) Math.ceil((double) total / pageSize));
        return pb;
    }

    /**
     * 使用 SCAN 删除所有主页列表缓存（避免 keys() 阻塞 Redis）
     */
    private void deleteHomeListCache() {
        Set<String> keysToDelete = new HashSet<>();
        try {
            Cursor<String> cursor = stringRedisTemplate.scan(
                    ScanOptions.scanOptions()
                            .match(RedisConstants.HOME_LIST_KEY + "*")
                            .count(100)
                            .build());
            while (cursor.hasNext()) {
                keysToDelete.add(cursor.next());
            }
            cursor.close();
        } catch (Exception e) {
            keysToDelete = stringRedisTemplate.keys(RedisConstants.HOME_LIST_KEY + "*");
        }
        if (keysToDelete != null && !keysToDelete.isEmpty()) {
            stringRedisTemplate.delete(keysToDelete);
        }
    }

}
