package com.itheima.big_event.service.Impl;

import com.itheima.big_event.mapper.ArticleMapper;
import com.itheima.big_event.mapper.CategoryMapper;
import com.itheima.big_event.pojo.Category;
import com.itheima.big_event.service.CategoryService;
import com.itheima.big_event.utils.ThreadLocalUtil;
import org.apache.ibatis.annotations.Param;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class CategoryServiceImpl implements CategoryService {
    @Autowired
    private CategoryMapper categoryMapper;
    @Autowired
    private ArticleMapper articleMapper;
    //添加分类（管理员操作，create_user 为 null 表示全局分类）
    @Override
    public void addCategory(Category category) {
        category.setCreateTime(LocalDateTime.now());
        category.setUpdateTime(LocalDateTime.now());
        // 前端没有别名字段，给默认值
        if (category.getCategoryAlias() == null) {
            category.setCategoryAlias("");
        }
        categoryMapper.add(category);
    }

    @Override
    public List<Category> List() {
        return categoryMapper.List();
    }

    @Override
    public Category findById(Integer id) {
        Category c = categoryMapper.findById(id);
        return c;
    }

    @Override
    public void updateCategory(Category category) {
        //补充更新时间
        category.setUpdateTime(LocalDateTime.now());
        if (category.getCategoryAlias() == null) {
            category.setCategoryAlias("");
        }
        categoryMapper.update(category);
    }

    @Override
    public void deleteCategory(Integer id) {
        //根据id删除对应的id的分类
        Map<String,Object> map = ThreadLocalUtil.get();
        Integer userId = (Integer) map.get("id");
        Category category = categoryMapper.findById(id);
        if (category == null) {
            throw new RuntimeException("分类不存在");
        }
        //获取用户的未分类id
        Category defaultCategoryId = categoryMapper.findDefaultCategoryid(userId);
        articleMapper.updateCategoryToDefault(id,defaultCategoryId.getId(),userId);
        categoryMapper.delete(userId,id);
    }
}
