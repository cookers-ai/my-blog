package com.miniblog.mapper;

import com.miniblog.pojo.PageBean;
import com.miniblog.pojo.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface UserMapper {
    //根据用户名查询用户
    @Select("select *from user where username=#{userName}")
    public User findByUserName(String userName) ;

    //添加用户
    @Insert("insert into user(username,password,create_time,update_time,role) values(#{username},#{password},now(),now(),#{role})")
    public void add(User user);
    //更新用户信息
    @Update("update user set nickname=#{nickname},email=#{email}, update_time=now() where id=#{id}")
    void update(User user);

    //更新用户头像
    @Update("update user set user_pic=#{avatar}, update_time=now() where id=#{id}")
    void updateAvatar(String avatar,Integer id);
    //更新用户密码
    @Update("update user set password=#{newPassword}, update_time=now() where id=#{id}")
    void updatePassword(String newPassword,Integer id);
    //查询所有用户
    @Select("select *from user")
    List<User> findAll(Integer pageNum, Integer pageSize);
    //根据id列表批量查询用户
    @Select("<script>select * from user where id in <foreach collection='ids' item='id' open='(' close=')' separator=','>#{id}</foreach></script>")
    List<User> selectBatchIds(@Param("ids") List<Integer> ids);

    //根据用户id查询用户信息
    @Select("select *from user where id=#{userId}")
    User findById(Integer userId);
}
