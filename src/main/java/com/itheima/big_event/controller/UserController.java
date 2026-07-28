package com.itheima.big_event.controller;

import cn.hutool.core.bean.BeanUtil;
import com.alibaba.fastjson.JSON;
import com.itheima.big_event.DTO.userDTO;
import com.itheima.big_event.DTO.userMessageDTO;
import com.itheima.big_event.mapper.CategoryMapper;
import com.itheima.big_event.pojo.Category;
import com.itheima.big_event.pojo.Result;
import com.itheima.big_event.pojo.User;
import com.itheima.big_event.service.UserService;
import com.itheima.big_event.utils.JwtUtil;
import com.itheima.big_event.utils.RedisConstants;
import com.itheima.big_event.utils.ThreadLocalUtil;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.constraints.URL;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static net.sf.jsqlparser.util.validation.metadata.NamedObject.user;

@RestController
@RequestMapping("/user")
@Validated
public class UserController {
    @Autowired
    private UserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private com.itheima.big_event.mapper.ArticleMapper articleMapper;

    @PostMapping("/register")
    public Result register(@Pattern(regexp = "^\\S{5,16}$") String userName, String password) {
        User u = userService.findByUserName(userName);
        if (u != null) {
            return Result.error("用户已经存在");
        }
        userService.register(userName, password);
        return Result.success("注册成功");
    }

    @PostMapping("/login")
    public Result login(@Pattern(regexp = "^\\S{5,16}$") String userName, String password) {
        User u = userService.findByUserName(userName);
        if (u != null) {
            if (!userService.checkPassword(password, u.getPassword())) {
                return Result.error("密码不一致");
            }
            Map<String, Object> Claims = new HashMap<>();
            Claims.put("id", u.getId());
            Claims.put("username", u.getUsername());
            Claims.put("role", u.getRole());
            Claims.put("userStatus", u.getUserStatus());
            String token = JwtUtil.genToken(Claims);
            String redisKey = RedisConstants.LONG_USER_KEY + token;
            stringRedisTemplate.opsForValue().set(
                    redisKey,
                    String.valueOf(u.getUserStatus()),
                    RedisConstants.TOKEN_EXPIRE_TIME,
                    TimeUnit.MINUTES
            );
            return Result.success(token);
        }
        return Result.error("用户不存在");
    }
    @GetMapping("/userinfo")
    public Result<User> userinfo() {
        Map<String, Object> Claims = ThreadLocalUtil.get();
        String username = (String) Claims.get("username");
        User user = userService.findByUserName(username);
        return Result.success(user);
    }

    @PostMapping("/update")
    public Result update(@RequestBody @Validated User user) {
        userService.update(user);
        return Result.success("更新成功");
    }

    @PatchMapping("/updateavatar")
    public Result updateAvatar(@RequestParam @URL String avatar) {
        userService.updataAvatar(avatar);
        return Result.success("更新成功");
    }

    @PatchMapping("/updatepassword")
    public Result updatePassword(@RequestBody Map<String, String> map) {
        String oldPassword = map.get("oldPassword");
        String newPassword = map.get("newPassword");
        String confirmPassword = map.get("confirmPassword");
        if (!StringUtils.hasLength(oldPassword) || !StringUtils.hasLength(newPassword) || !StringUtils.hasLength(confirmPassword)) {
            return Result.error("密码不能为空");
        }
        Map<String, Object> Claims = ThreadLocalUtil.get();
        String username = (String) Claims.get("username");
        User loginUser = userService.findByUserName(username);
        if (!userService.checkPassword(oldPassword, loginUser.getPassword())) {
            return Result.error("原密码错误");
        }
        if (!newPassword.equals(confirmPassword)) {
            return Result.error("两次填写的新密码不一致");
        }
        userService.updatePassword(newPassword);
        return Result.success("更新成功");
    }

    @GetMapping("/userinfo/{userId}")
    public Result userinfo(@PathVariable Integer userId) {
        User user = userService.findById(userId);
        userMessageDTO userMessageDTO = new userMessageDTO();
        BeanUtils.copyProperties(user, userMessageDTO);
        userMessageDTO.setArticleCount(articleMapper.countByUserId(userId));
        return Result.success(userMessageDTO);
    }
}
