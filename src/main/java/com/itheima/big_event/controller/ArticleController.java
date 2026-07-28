package com.itheima.big_event.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.itheima.big_event.pojo.*;
import com.itheima.big_event.service.ArticleService;
import com.itheima.big_event.service.CommentService;
import com.itheima.big_event.service.LikeService;
import com.itheima.big_event.utils.CacheClient;
import com.itheima.big_event.utils.RedisConstants;
import com.itheima.big_event.utils.ThreadLocalUtil;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;


@RestController
@RequestMapping("/article")
public class ArticleController {
    @Autowired
    private ArticleService articleService;
    @Autowired
    private LikeService likeService;
    @Autowired
    private CommentService commentService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;
    @GetMapping("/homeList")
    public Result<PageBean<Article>> homeList(@RequestParam(defaultValue = "1") Integer pageNum,
                                              @RequestParam(defaultValue = "10") Integer pageSize,
                                              @RequestParam(required = false) Integer categoryId,
                                              @RequestParam(required = false) String sort){
        String cacheKey = String.format("%d:%d:%s:%s", pageNum, pageSize,
                categoryId == null ? "all" : categoryId.toString(),
                StrUtil.isBlank(sort) ? "default" : sort);
        String keyPrefix = RedisConstants.HOME_LIST_KEY + cacheKey + ":";
        //小于4页查询redis缓存
        if(pageNum<4){
            return Result.success(cacheClient.queryWithLogicalExpirePage(keyPrefix,cacheKey,Article.class,
                    (v)->articleService.getHomeList(pageNum, pageSize, categoryId, sort),
                    RedisConstants.HOME_LIST_EXPIRE_TIME,TimeUnit.SECONDS));
        }
        else {
            return Result.success(articleService.getHomeList(pageNum, pageSize, categoryId, sort));
        }


    }
    @GetMapping("/category/List")
    public Result<List<ArticleCategory>> categoryList(){
        List<ArticleCategory> categoryList = articleService.categoryList();
        return Result.success(categoryList);
    }
    @PostMapping("/addArticle")
   public Result addArticle(@RequestBody @Validated Article article){
        articleService.addArticle(article);
       return Result.success();
   }
   @GetMapping("/of/follow")
   public Result queryBlogOfFollow(@RequestParam(value = "lastId",defaultValue = "0") long max,
                                   @RequestParam("offset") Integer offset){
        return articleService.queryBlogOfFollow(max,offset);


   }
   @GetMapping("/user/{userId}")
   public Result<PageBean<Article>> userArticles(
           @PathVariable Integer userId,
           @RequestParam(defaultValue = "1") Integer pageNum,
           @RequestParam(defaultValue = "10") Integer pageSize) {
       return Result.success(articleService.getUserArticles(userId, pageNum, pageSize));
   }
    @GetMapping("/articleList")
    public Result<PageBean<Article>> articleList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) String state
    ) {
        PageBean<Article> pageBean = articleService.list(pageNum, pageSize, categoryId, state);
        return Result.success(pageBean);

    }
    @GetMapping("/articleDetail")
    public Result<Article> articleDetail(Integer id) {
        Article article = articleService.getArticle(id);
        if (article == null) {
            return Result.error();
        }
        Map<String, Object> userInfo = ThreadLocalUtil.get();
        if (userInfo != null && userInfo.get("id") != null) {
            boolean isLike = likeService.isLikeArticle(id);
            article.setIsLike(isLike);
        } else {
            article.setIsLike(false);
        }

        return Result.success(article);
    }



    @PutMapping("/updateArticle")
    public Result updateArticle(@RequestBody @Validated Article article){
        Integer id = article.getId();
        String keyRedis = RedisConstants.ARTICLE_DETAIL_KEY + id;
        if(id==null){
            return Result.error();
        }
        articleService.updateArticle(article);
        stringRedisTemplate.delete(keyRedis);
        return Result.success();
    }
    @DeleteMapping("/deleteArticle")
    public Result deleteArticle(@RequestParam Integer id){
        stringRedisTemplate.delete(RedisConstants.ARTICLE_DETAIL_KEY + id);
        stringRedisTemplate.delete(RedisConstants.ARTICLE_DETAIL_COUNT_KEY + id);
        articleService.deleteArticle(id);
        return Result.success();
    }
    @PostMapping("/likeArticle/{articleId}")
    public Result likeArticle(@PathVariable Integer articleId){
        likeService.likeArticle(articleId);
        return Result.success();
    }
    @GetMapping("/like/{articleId}")
    public Result<Boolean> isLiked(@PathVariable Integer articleId) {
        boolean liked = likeService.isLikeArticle(articleId);
        return Result.success(liked);
    }
    @PostMapping("/addComment")
    public Result addComment(@RequestBody  Comment comment) {
        commentService.addComment(comment);
        return Result.success();
    }
    //获取评论列表（分页）
    @GetMapping("/comment/{articleId}")
    public Result<PageBean<Comment>> commentList(
            @PathVariable Integer articleId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        PageBean<Comment> pb = commentService.commentList(articleId, pageNum, pageSize);
        return Result.success(pb);
    }
    @DeleteMapping("/comment/{id}")
    public Result deleteComment(@PathVariable Long id) {
        commentService.deleteComment(id);
        return Result.success();
    }

}
