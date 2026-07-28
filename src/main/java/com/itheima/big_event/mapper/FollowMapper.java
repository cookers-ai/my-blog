package com.itheima.big_event.mapper;

import com.itheima.big_event.pojo.Follow;
import com.itheima.big_event.pojo.User;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface FollowMapper {
    //根据关注者id和被关注者id查询关注记录
    @Select("select count(*) from follow where follower_id = #{userId} and followee_id = #{followeeId}")
    int isFollow(Integer userId, Integer followeeId);
    //关注用户
    @Insert("insert into follow (follower_id, followee_id, create_time) values (#{followerId}, #{followeeId}, #{createTime})")
    void insert(Follow follow);
    //更新用户关注数（delta: 1=关注 -1=取关）
    @Update("update user set follow_count = follow_count + #{delta} where id = #{userId}")
    void updateFollowCount(Integer userId, int delta);
    //更新被关注用户粉丝数（delta: 1=关注 -1=取关）
    @Update("update user set fans_count = fans_count + #{delta} where id = #{followeeId}")
    void updateFansCount(Integer followeeId, int delta);
    //取关用户
    @Delete("delete from follow where follower_id = #{userId} and followee_id = #{followeeId}")
    void deleteByUserAndFollowee(Integer userId, Integer followeeId);
    //根据用户id查询粉丝（谁 followee_id = me）
    @Select("select * from follow where followee_id = #{userId}")
    List<Follow> findFollowers(Integer userId);
    //根据用户id查询关注列表（谁 follower_id = me）
    @Select("select * from follow where follower_id = #{userId}")
    List<Follow> findFollowees(Integer userId);
    //查询关注用户列表（联表 + 按关注时间倒序，用于分页兜底）
    @Select("select u.id, u.nickname, u.user_pic, u.username, f.create_time as followTime " +
            "from follow f inner join user u on f.followee_id = u.id " +
            "where f.follower_id = #{userId} order by f.create_time desc")
    List<User> selectFolloweeUsers(Integer userId);
    //查询粉丝列表（联表 + 按关注时间倒序，用于分页）
    @Select("select u.id, u.nickname, u.user_pic, u.username, f.create_time as followTime " +
            "from follow f inner join user u on f.follower_id = u.id " +
            "where f.followee_id = #{userId} order by f.create_time desc")
    List<User> selectFollowerUsers(Integer userId);
}
