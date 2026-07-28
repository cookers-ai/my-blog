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
@Validated//开启校验
public class UserController {
    //引入UserService服务
    @Autowired
    private  UserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private com.itheima.big_event.mapper.ArticleMapper articleMapper;

    //注册
    @PostMapping("/register")
    public Result register(@Pattern(regexp = "^\\S{5,16}$") String userName, String password) {
        //查询用户是否存在
        User u=userService.findByUserName(userName);
        if(u!=null){
            return Result.error("用户已经存在");
        }
        //注册用户
        userService.register(userName,password);
        return Result.success("注册成功");
    }
    //登录
    @PostMapping("/login")
    public Result login(@Pattern(regexp = "^\\S{5,16}$") String userName, String password) {
        //查询用户是否存在
        User u=userService.findByUserName(userName);
        if(u!=null){
            //判断密码是否一致
            if(!userService.checkPassword(password, u.getPassword())){
                return Result.error("密码不一致");
            }
            else {
                //登录成功,生成token
                Map<String,Object> Claims=new HashMap<>();
                Claims.put("id",u.getId());
                Claims.put("username",u.getUsername());
                Claims.put("role",u.getRole());
                Claims.put("userStatus",u.getUserStatus());
                //生成token
               String token=JwtUtil.genToken(Claims);
               String redisKey=RedisConstants.LONG_USER_KEY+token;
                stringRedisTemplate.opsForValue().set(
                        redisKey,
                        String.valueOf(u.getUserStatus()),
                        RedisConstants.TOKEN_EXPIRE_TIME,
                        TimeUnit.MINUTES
                );
                //返回token
                return Result.success(token);
            }
        }
        else {
            return Result.error("用户不存在");
        }
    }
    /*
     * 查询用户信息
     */
    @GetMapping("/userinfo")
    public  Result<User> userinfo(){
        //从ThreadLocal中获取用户名
        Map<String,Object> Claims=ThreadLocalUtil.get();
        //从Claims中获取用户名
        String username=(String) Claims.get("username");
        //根据用户名查询用户
        User user=userService.findByUserName(username);
        //返回用户信息
        return Result.success(user);
    }
    /*
    更新用户信息
    @RequestBody User user 用户信息:浏览器会发送一个json字符串里面包含用户信息@RequestBody注解将json字符串转换为user对象
    @Validated 校验注解:开启在实体类上的校验注解,校验实体类上的注解是否符合要求

     */
    @PostMapping("/update")
    public Result update(@RequestBody @Validated User user){
        //调用UserService中的update方法更新用户信息
        userService.update(user);
        return Result.success("更新成功");
    }
    /*
     * 更新用户头像
     * String avatar 头像路径
     * @RequestParam 注解:将浏览器发送的请求参数转换为方法参数
     * @URL 校验注解:校验头像路径是否为正确的url格式
     * */
    @PatchMapping("/updateavatar")
    public Result updateAvatar(@RequestParam @URL String avatar){
        userService.updataAvatar(avatar);
        return Result.success("更新成功");
    }
    /*
     * 更新用户密码
     * Map<String,String> map 密码信息:浏览器会发送一个json字符串里面包含旧密码和新密码
     * 为oldPassword 旧密码，newPassword 新密码，confirmPassword 确认密码
     * 这时候浏览器传递的json字符串为:
     * {
     *     "oldPassword":"123456",
     *     "newPassword":"123456",
     *     "confirmPassword":"123456"
     * }
     * 与实体类不一致,所以需要使用Map来接收密码信息
     * */
    @PatchMapping("/updatepassword")
    public Result updatePassword( @RequestBody Map<String,String> map){
        //校验参数
        String oldPassword=map.get("oldPassword");
        String newPassword=map.get("newPassword");
        String confirmPassword=map.get("confirmPassword");
        //判断密码是否为空
        if(!StringUtils.hasLength(oldPassword)||!StringUtils.hasLength(newPassword)||!StringUtils.hasLength(confirmPassword)){
            return Result.error("密码不能为空");
        }
        //判断密码是否正确
        //从ThreadLocal中获取用户名
        Map<String,Object> Claims=ThreadLocalUtil.get();
        //从Claims中获取用户名
        String username=(String) Claims.get("username");
        //根据用户名查询用户
        User loginUser=userService.findByUserName(username);
        //遗漏代码：取出的密码此时是加密的需要将newPassword加密后与loginUser.getPassword()比较
        if(!userService.checkPassword(oldPassword, loginUser.getPassword())){
            return Result.error("原密码错误");
        }
        //判断新密码是否一致
        if(!newPassword.equals(confirmPassword)){
            return Result.error("两次填写的新密码不一致");
        }
        //更新用户密码
        userService.updatePassword(newPassword);
        return Result.success("更新成功");
    }
    /*
    * 查看单体用户信息
    * @param userId 用户id
    */
    @GetMapping("/userinfo/{userId}")
    public Result userinfo(@PathVariable Integer userId){
        //根据用户id查询用户信息
        User user=userService.findById(userId);
        //将用户信息转换为userMessageDTO对象
        userMessageDTO userMessageDTO=new userMessageDTO();
        BeanUtils.copyProperties(user,userMessageDTO);
        //填充文章数
        userMessageDTO.setArticleCount(articleMapper.countByUserId(userId));
        //返回用户信息
        return Result.success(userMessageDTO);
    }
}
