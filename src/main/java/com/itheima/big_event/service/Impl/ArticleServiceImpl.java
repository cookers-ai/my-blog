package com.itheima.big_event.service.Impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.itheima.big_event.DTO.ArticleCountDTO;
import com.itheima.big_event.DTO.ScrollResultDTO;
import com.itheima.big_event.mapper.ArticleMapper;
import com.itheima.big_event.mapper.FollowMapper;
import com.itheima.big_event.mapper.UserMapper;
import com.itheima.big_event.pojo.*;
import com.itheima.big_event.service.ArticleService;
import com.itheima.big_event.service.LikeService;
import com.itheima.big_event.utils.CacheClient;
import com.itheima.big_event.utils.RedisConstants;
import com.itheima.big_event.utils.ThreadLocalUtil;
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
        //补充信息
        //补充创建时间
        article.setCreateTime(LocalDateTime.now());
        //补充更新时间
        article.setUpdateTime(LocalDateTime.now());
        //补充创建人的id
        //从登录拦截器中获取当前登录用户的id
        Map<String, Object> map = ThreadLocalUtil.get();
        Integer userId = (Integer) map.get("id");
        //补充创建人的id
        article.setCreateUser(userId);
        //如果图片为空，默认图片为默认图片
        if (article.getCoverImg() == null) {
            article.setCoverImg("");
        }
        boolean isSuccess = articleMapper.add(article);
        if(!isSuccess){
            return Result.error("添加文章失败");
        }
        // 清除主页列表缓存（只有 pageNum 1-3 走缓存，用 SCAN 避免阻塞 Redis）
        deleteHomeListCache();
        //根据用户查找粉丝（关注了作者的人）
        List<Follow> fanslist = followMapper.findFollowers(userId);
        //遍历集合，往每个粉丝的收件箱推送文章
        for (Follow fan : fanslist) {
            Integer followerId = fan.getFollowerId();
            String key = RedisConstants.FANS_KEY_PREFIX + followerId;
            stringRedisTemplate.opsForZSet()
                    .add(key, article.getId().toString(), System.currentTimeMillis());
            // 设置收件箱过期时间为10天（每次推送续期，活跃用户不会过期）
            stringRedisTemplate.expire(key, RedisConstants.FANS_INBOX_EXPIRE_TIME, TimeUnit.SECONDS);
        }
        return Result.success();
    }

    @Override
    public PageBean<Article> list(Integer pageNum, Integer pageSize, Integer categoryId, String state) {
        //创建pageBean对象用来封装查询好的数据
        PageBean<Article> ppb = new PageBean<>();
        //开启分页查询(pageHelper)
        //返回List对象
        PageHelper.startPage(pageNum, pageSize);
        //获取当前userid
        Map<String, Object> map = ThreadLocalUtil.get();
        Integer userId = (Integer) map.get("id");
        if (userId == null) {
            throw new IllegalStateException("用户未登录，无法查询文章列表");
        }
        //根据用户id查询文章列表
        List<Article> list = articleMapper.list(userId, state, categoryId);
        //Page中提供了方法可以获取PageHelper分类查询后的结果（总记录数、当前页记录、总页数等）
        //将List对象强转为Page对象
        Page<Article> page = (Page<Article>) list;
        //将数据填充到PageBean对象中
        ppb.setTotal(page.getTotal());
        ppb.setItems(page.getResult());

        return ppb;
    }
    //查询文章详情
    @Override
    public Article detail(Integer id) {
        return articleMapper.findById(id);
    }

    @Override
    public void updateArticle(Article article) {
        //补充更新时间
        article.setUpdateTime(LocalDateTime.now());
        articleMapper.update(article);
    }

    @Override
    public void deleteArticle(Integer id) {
        //根据id删除对应的id的文章
        Map<String, Object> map = ThreadLocalUtil.get();
        Integer userId = (Integer) map.get("id");
        articleMapper.delete(userId, id);
    }

    @Override
    public PageBean<Article> adminList(Integer pageNum, Integer pageSize, Integer categoryId, String userId, String state) {
        //创建pageBean对象用来封装查询好的数据
        PageBean<Article> ppb = new PageBean<>();
        //开启分页查询(pageHelper)
        //返回List对象
        PageHelper.startPage(pageNum, pageSize);
        //根据用户id查询文章列表
        List<Article> list = articleMapper.adminList(userId, state, categoryId);
        //Page中提供了方法可以获取PageHelper分类查询后的结果（总记录数、当前页记录、总页数等）
        //将List对象强转为Page对象
        Page<Article> page = (Page<Article>) list;
        //将数据填充到PageBean对象中
        ppb.setTotal(page.getTotal());
        ppb.setItems(page.getResult());

        return ppb;
    }
    //查询首页文章列表
    @Override
    public PageBean<Article> getHomeList(Integer pageNum, Integer pageSize, Integer categoryId, String sort) {
        //统计总数
        Long total = articleMapper.count(categoryId);
        //计算偏移量
        int start = (pageNum - 1) * pageSize;
        //按条件查询文章列表
        List<Article> list = articleMapper.selectByPage(categoryId, sort, start, pageSize);
        // 批量填充作者信息
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
        //将数据填充到PageBean对象中
        PageBean<Article> ppb = new PageBean<>();
       // ppb.setTotal(total);
        ppb.setItems(list);
        //计算当前页码
        ppb.setPage((int) Math.ceil((double) total / pageSize));
        return ppb;
    }

    @Override
    public List<ArticleCategory> categoryList() {

        return articleMapper.categoryList();
    }
    //实时查询文章点赞数和评论数和浏览量并放到article对象中
    @Override
    public void enrichCounts(Article article) {
        //查询文章点赞数和评论数和浏览量
        ArticleCountDTO countDTO = articleMapper.selectCounts(article.getId());
        if(countDTO==null){
            return;
        }
        //将数据填充到article对象中
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

        // 1. 获取文章主体（带互斥锁防击穿）
        Article article = cacheClient.articleDetailMutexLock(keyRedis,
                id, Article.class,
                this::detail,
                expireTime, TimeUnit.SECONDS);

        // 文章不存在，直接返回，不递增浏览量
        if (article == null) {
            return null;
        }

        // 填充作者信息（昵称和头像）
        if (article.getCreateUser() != null) {
            User author = userMapper.findById(article.getCreateUser());
            if (author != null) {
                article.setAuthorName(getUserDisplayName(author));
                article.setAuthorAvatar(author.getUserPic());
            }
        }

        // 2. 填充动态计数（like/comment/view）
        Map<Object, Object> countMap = stringRedisTemplate.opsForHash().entries(CountRedis);
        if (!countMap.isEmpty()) {
            // 缓存命中
            extracted(article, countMap);
        } else {
            // 缓存未命中，加互斥锁重建
            boolean locked = false;
            try {
                locked = cacheClient.tryLockWithRetry(lockKey, 5, 100);
                if (locked) {
                    // ★ Double Check：前一个线程可能已重建完成
                    countMap = stringRedisTemplate.opsForHash().entries(CountRedis);
                    if (!countMap.isEmpty()) {
                        extracted(article, countMap);
                    } else {
                        // 确认缓存空，查 DB 并回填
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
                    // 没抢到锁，降级直接查 DB（不写缓存，避免竞态）
                    this.enrichCounts(article);
                }
            } catch (Exception e) {
                // 不调用 interrupt()，避免 releaseLock 中的 Redis 操作因中断标记而失败
                throw new RuntimeException("缓存重建被中断", e);
            } finally {
                if (locked) {
                    cacheClient.releaseLock(lockKey);
                }
            }
        }

        // 递增浏览量
        stringRedisTemplate.opsForHash().increment(CountRedis, "viewCount", 1);
        return article;
    }
//查询关注文章列表
    @Override
    public Result queryBlogOfFollow(long max, Integer offset) {
        //获取当前登录用户id
        Map<String, Object> userInfo = ThreadLocalUtil.get();
        Integer userId = (Integer) userInfo.get("id");
        //获取收件箱
        String key=RedisConstants.FANS_KEY_PREFIX+userId;
        //分页查询
        Set<ZSetOperations.TypedTuple<String>> tuples = stringRedisTemplate.
                opsForZSet().reverseRangeByScoreWithScores(key,0,max,offset,5);
        if (tuples == null || tuples.isEmpty()) {
            // Redis 为空（可能已过期），降级查 DB：取关注用户的已发布文章
            List<Follow> follows = followMapper.findFollowees(userId);
            if (follows == null || follows.isEmpty()) {
                return Result.success(new ScrollResultDTO());
            }


            List<Integer> followeeIds = new ArrayList<>();
            for (Follow f : follows) { followeeIds.add(f.getFolloweeId()); }
            List<Article> dbArticles = articleMapper.selectByUsers(followeeIds);
            // 填充作者信息
            if (!dbArticles.isEmpty()) {
                Set<Integer> ids2 = new HashSet<>();
                for (Article a : dbArticles) { if (a.getCreateUser() != null) ids2.add(a.getCreateUser()); }
                if (!ids2.isEmpty()) {
                    List<User> users = userMapper.selectBatchIds(new ArrayList<>(ids2));
                    Map<Integer, User> m = new HashMap<>();
                    for (User u : users) m.put(u.getId(), u);
                    for (Article a : dbArticles) {
                        User u = m.get(a.getCreateUser());
                        if (u != null) { a.setAuthorName(getUserDisplayName(u)); a.setAuthorAvatar(u.getUserPic()); }
                    }
                }
                // 填充点赞状态
                for (Article a : dbArticles) {
                    a.setIsLike(likeService.isLikeArticle(a.getId()));
                }

                // ==== 回写 Redis 收件箱（DB 兜底后重建缓存）====
                for (Article a : dbArticles) {
                    if (a.getCreateTime() != null) {
                        long score = a.getCreateTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                        stringRedisTemplate.opsForZSet().add(key, a.getId().toString(), score);
                    } else {
                        stringRedisTemplate.opsForZSet().add(key, a.getId().toString(), System.currentTimeMillis());
                    }
                }
                // 设置 10 天过期
                stringRedisTemplate.expire(key, RedisConstants.FANS_INBOX_EXPIRE_TIME, TimeUnit.SECONDS);
            }
            ScrollResultDTO fallback = new ScrollResultDTO();
            fallback.setList(dbArticles);
            fallback.setMinTime(0);
            fallback.setOffset(1);
            return Result.success(fallback);
        }
        //解析数据
        //装文章id
        List<Long> ids = new ArrayList<>(tuples.size());
        long score = 0;
        //代表偏移量：最小值出现的次数
        int off = 1;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            String articleIds = tuple.getValue();
            ids.add(Long.parseLong(articleIds));
             Long time = tuple.getScore().longValue();
             if(time==score){
                 //如果时间相等，偏移量加1
                 off++;
             }
            else {
                //如果时间不相等，更新最小值
                score=time;
                //如果时间不相等，偏移量重置为1
                off=1;
            }
        }
        //根据文章id查询文章列表（保证顺序一致）
        List<Article> articles = articleMapper.selectBatchIds(ids);
        // ===== 批量收集作者ID =====
        Set<Integer> authorIds = new HashSet<>();
        for (Article article : articles) {
            authorIds.add(article.getCreateUser());
        }

        // ===== 批量查询作者信息 =====
        Map<Integer, User> userMap = new HashMap<>();
        if (!authorIds.isEmpty()) {
            List<User> users = userMapper.selectBatchIds(new ArrayList<>(authorIds));
            for (User user : users) {
                userMap.put(user.getId(), user);
            }
        }

        // ===== 遍历文章列表，补全信息 =====
        for (Article article : articles) {
            // ① 查看article有关的用户——设置作者昵称和头像
            User author = userMap.get(article.getCreateUser());
            if (author != null) {
                //设置作者昵称
                article.setAuthorName(getUserDisplayName(author));
                //设置作者头像
                article.setAuthorAvatar(author.getUserPic());
            }

            // ② 查看article是否被点过赞
            boolean isLike = likeService.isLikeArticle(article.getId());
            article.setIsLike(isLike);
        }
        //封装结果
        ScrollResultDTO scrollResult= new ScrollResultDTO();
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
        // 填充作者信息（这里作者就是该用户自己，从缓存或查库）
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
            // SCAN 失败时降级使用 keys
            keysToDelete = stringRedisTemplate.keys(RedisConstants.HOME_LIST_KEY + "*");
        }
        if (keysToDelete != null && !keysToDelete.isEmpty()) {
            stringRedisTemplate.delete(keysToDelete);
        }
    }

}

