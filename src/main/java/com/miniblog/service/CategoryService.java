package com.miniblog.service;

import com.miniblog.pojo.Category;

import java.util.List;

public interface CategoryService {
    //添加分类
    void addCategory(Category category);

    //查询所有分类
    List<Category> List();

    //根据id查询分类详情
    Category findById(Integer id);
    //更新分类
    void updateCategory(Category category);
    //删除分类
    void deleteCategory(Integer id);
}
