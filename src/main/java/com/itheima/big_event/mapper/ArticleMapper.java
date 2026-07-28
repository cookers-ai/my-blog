package com.itheima.big_event.mapper;

import com.github.pagehelper.Page;
import com.itheima.big_event.DTO.ArticleCountDTO;
import com.itheima.big_event.pojo.Article;
import com.itheima.big_event.pojo.ArticleCategory;
import com.itheima.big_event.pojo.Category;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ArticleMapper {
    //添加文章
    @Insert("insert into article(title,content,cover_img,state,category_id,create_user,create_time,update_time) " +
            "values(#{title},#{content},#{coverImg},#{state},#{categoryId},#{createUser},#{createTime},#{updateTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    boolean add(Article article);
    //根据用户id标题查询所有文章
    List<Article> list(@Param("userId") Integer userId, @Param("state") String state, @Param("categoryId") Integer categoryId);
    //根据文章id查询文章详情
    @Select("select * from article where id = #{id}")
    Article findById(Integer id);
    //更新文章
    void update(Article article);
    //删除文章
    @Delete("delete from article where create_user = #{userId} and id = #{id}")
    void delete(Integer userId,Integer id);
    //更新文章分类为默认分类
    @Update("update article set category_id = #{newCategoryId} where create_user = #{userId} and category_id = #{oldCategoryId}")
    void updateCategoryToDefault(@Param("oldCategoryId") Integer oldCategoryId,
                                @Param("newCategoryId") Integer newCategoryId,
                                @Param("userId") Integer userId);
    //管理员查询所有文章
    List<Article> adminList(@Param("userId") String userId, @Param("state") String state, @Param("categoryId") Integer categoryId);
    //统计总数
    Long count(@Param("categoryId") Integer categoryId);
    //按条件查询文章列表
    List<Article> selectByPage(@Param("categoryId") Integer categoryId, @Param("sort") String sort, @Param("start") int start, @Param("pageSize") Integer pageSize);
    //查询所有分类
    @Select("select * from article_category")
    List<ArticleCategory> categoryList();

    //减少文章点赞数
    @Update("update article set like_count = like_count - 1 where id = #{articleId}")
    void decrLikeCount(Integer articleId);
//增加文章点赞数
    @Update("update article set like_count = like_count + 1 where id = #{articleId}")
    void incrLikeCount(Integer articleId);
    //增加文章评论数
    @Update("update article set comment_count = comment_count + 1 where id = #{articleId}")
    void incrCommentCount(Integer articleId);
    //减少文章评论数
    @Update("update article set comment_count = comment_count - 1 where id = #{articleId}")
    void decrCommentCount(Integer articleId);
    //查询文章点赞数和评论数和浏览量
    @Select("select like_count,comment_count,view_count from article where id = #{id}")
    ArticleCountDTO selectCounts(Integer id);
    //根据文章id查询浏览量
    @Select("select view_count from article where id = #{id}")
    Integer selectViewCount(Integer id);
//更新文章浏览量
    @Update("update article set view_count = #{viewCountInt} where id = #{articleId}")
    void updateViewCount(int articleId, int viewCountInt);
//根据文章id查询文章列表（保证顺序一致）
    @Select("select * from article where id in (#{ids}) order by id")
    List<Article> selectBatchIds(List<Long> ids);
    //根据用户id列表查询已发布文章（关注流 Redis 为空时兜底）
    @Select("<script>select * from article where create_user in <foreach collection='userIds' item='id' open='(' close=')' separator=','>#{id}</foreach> and state = '已发布' order by create_time desc</script>")
    List<Article> selectByUsers(@Param("userIds") List<Integer> userIds);

    //统计某用户的已发布文章数
    @Select("select count(*) from article where create_user = #{userId} and state = '已发布'")
    long countByUserId(Integer userId);

    //查询某用户的已发布文章列表（分页）
    @Select("select id, title, cover_img, create_time, category_id, create_user, view_count, like_count, comment_count " +
            "from article where create_user = #{userId} and state = '已发布' order by create_time desc " +
            "limit #{start}, #{pageSize}")
    List<Article> selectByUserId(@Param("userId") Integer userId,
                                 @Param("start") int start,
                                 @Param("pageSize") int pageSize);
}
