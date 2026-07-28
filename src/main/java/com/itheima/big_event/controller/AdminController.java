package com.itheima.big_event.controller;

import com.itheima.big_event.anno.Log;
import com.itheima.big_event.anno.RequireAdmin;
import com.itheima.big_event.pojo.*;
import com.itheima.big_event.service.*;
import com.itheima.big_event.utils.RedisConstants;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin")
@RequireAdmin
public class AdminController {
    @Autowired
    private UserService userService;
    @Autowired
    private ArticleService articleService;
    @Autowired
    private AdminService adminService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @GetMapping()
    public Result<PageBean<User>> queryUserList(@RequestParam(defaultValue = "1") Integer pageNum,
                                                @RequestParam(defaultValue = "10") Integer pageSize) {
        PageBean<User> pageBean = userService.findAll(pageNum, pageSize);
        return Result.success(pageBean);
    }

    @Log(module = "用户管理", operation = "查询用户")
    @GetMapping("/queryUser")
    public Result<User> queryUser(@RequestParam String userName) {
        User user = userService.findByUserName(userName);
        return Result.success(user);
    }

    @Log(module = "用户管理", operation = "查询用户文章列表")
    @GetMapping("/queryUserArticle")
    public Result<PageBean<Article>> queryUserArticle(@RequestParam(defaultValue = "1") Integer pageNum,
                                                      @RequestParam(defaultValue = "10") Integer pageSize,
                                                      @RequestParam(required = false) Integer categoryId,
                                                      @RequestParam(required = false) Integer userId) {
        String state = "已发布";
        PageBean<Article> pageBean = articleService.adminList(pageNum, pageSize, categoryId,
                userId == null ? null : userId.toString(), state);
        return Result.success(pageBean);
    }

    @Log(module = "用户管理", operation = "删除用户文章")
    @DeleteMapping("/deleteUserArticle")
    public Result deleteUserArticle(@RequestParam Integer id) {
        stringRedisTemplate.delete(RedisConstants.ARTICLE_DETAIL_KEY + id);
        stringRedisTemplate.delete(RedisConstants.ARTICLE_DETAIL_COUNT_KEY + id);
        adminService.adminDeleteArticle(id);
        return Result.success("删除成功");
    }

    @Log(module = "用户管理", operation = "下架文章")
    @PutMapping("/downArticle")
    public Result downArticle(@RequestParam Integer id) {
        String state = "草稿";
        adminService.updateArticleState(id, state);
        stringRedisTemplate.delete(RedisConstants.ARTICLE_DETAIL_KEY + id);
        stringRedisTemplate.delete(RedisConstants.ARTICLE_DETAIL_COUNT_KEY + id);
        return Result.success("下架成功");
    }

    @Log(module = "用户管理", operation = "批量封禁/解封用户")
    @PutMapping("/updateUserStatusBatch")
    public Result updateUserStatusBatch(@RequestBody List<Integer> ids, @RequestParam Integer userStatus) {
        adminService.updateUserStatuslist(ids, userStatus);
        return Result.success("更新成功");
    }

    @Log(module = "用户管理", operation = "封禁用户或者解封用户")
    @PutMapping("/updateUserStatus")
    public Result updateUserStatus(@RequestParam Integer id, @RequestParam Integer userStatus) {
        adminService.updateUserStatus(id, userStatus);
        return Result.success();
    }

    @Log(module = "用户管理", operation = "删除评论")
    @PostMapping("/deleteComment")
    public Result deleteComment(@RequestBody List<Long> ids) {
        adminService.deleteComment(ids);
        return Result.success("删除成功");
    }
}
