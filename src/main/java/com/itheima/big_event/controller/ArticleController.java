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
    /*
    * 主页文章列表*/
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
    /*
    * 分类列表*/
    @GetMapping("/category/List")
    public Result<List<ArticleCategory>> categoryList(){
        List<ArticleCategory> categoryList = articleService.categoryList();
        return Result.success(categoryList);
    }
    //添加文章
    @PostMapping("/addArticle")
   public Result addArticle(@RequestBody @Validated Article article){
        articleService.addArticle(article);
       return Result.success();
   }
   //查询关注文章列表
   @GetMapping("/of/follow")
   public Result queryBlogOfFollow(@RequestParam(value = "lastId",defaultValue = "0") long max,
                                   @RequestParam("offset") Integer offset){
        return articleService.queryBlogOfFollow(max,offset);


   }
   /*
   * 查询某用户的已发布文章列表（访客视角）
   */
   @GetMapping("/user/{userId}")
   public Result<PageBean<Article>> userArticles(
           @PathVariable Integer userId,
           @RequestParam(defaultValue = "1") Integer pageNum,
           @RequestParam(defaultValue = "10") Integer pageSize) {
       return Result.success(articleService.getUserArticles(userId, pageNum, pageSize));
   }
   /*
   * 文章列表（分页查询）
   * @RequestParam（required = false） 表示是否必填，默认false，不填时使用默认值
   * */
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
    /*
    * 文章详情
    * id:文章id
    * */
    @GetMapping("/articleDetail")
    public Result<Article> articleDetail(Integer id) {
        Article article = articleService.getArticle(id);
        if (article == null) {
            return Result.error();
        }

        // 查询当前用户是否已点赞（未登录默认 false）
        Map<String, Object> userInfo = ThreadLocalUtil.get();
        if (userInfo != null && userInfo.get("id") != null) {
            boolean isLike = likeService.isLikeArticle(id);
            article.setIsLike(isLike);
        } else {
            article.setIsLike(false);
        }

        return Result.success(article);
    }



    /**
    * 更新文章
    * */
    @PutMapping("/updateArticle")
    public Result updateArticle(@RequestBody @Validated Article article){
        Integer id = article.getId();
        String keyRedis = RedisConstants.ARTICLE_DETAIL_KEY + id;
        if(id==null){
            return Result.error();
        }
        articleService.updateArticle(article);
        //删除redis中的文章详情
        stringRedisTemplate.delete(keyRedis);
        return Result.success();
    }
    /*
     * 删除文章
     * @RequestParam 表示从请求参数中获取id(接收url参数中的id)
     * */
    @DeleteMapping("/deleteArticle")
    public Result deleteArticle(@RequestParam Integer id){
        //删除redis中的文章详情
        String keyRedis = RedisConstants.ARTICLE_DETAIL_KEY + id;
        stringRedisTemplate.delete(keyRedis);
        //删除redis中的文章点赞数和评论数和浏览量
        stringRedisTemplate.delete(RedisConstants.ARTICLE_DETAIL_COUNT_KEY + id);
        articleService.deleteArticle(id);
        return Result.success();
    }
    /*
    * 点赞文章
    * id:文章id
    * */
    @PostMapping("/likeArticle/{articleId}")
    public Result likeArticle(@PathVariable Integer articleId){
        likeService.likeArticle(articleId);
        return Result.success();
    }
    // 查询是否点赞
    @GetMapping("/like/{articleId}")
    public Result<Boolean> isLiked(@PathVariable Integer articleId) {
        boolean liked = likeService.isLikeArticle(articleId);
        return Result.success(liked);
    }
    //添加评论
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
    //删除评论
    @DeleteMapping("/comment/{id}")
    public Result deleteComment(@PathVariable Long id) {
        //删除评论
        commentService.deleteComment(id);
        return Result.success();
    }

}
