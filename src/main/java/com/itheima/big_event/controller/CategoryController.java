package com.itheima.big_event.controller;

import com.itheima.big_event.anno.RequireAdmin;
import com.itheima.big_event.pojo.Category;
import com.itheima.big_event.pojo.Result;
import com.itheima.big_event.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/category")
public class CategoryController {
    @Autowired
    private CategoryService categoryService;

    //添加分类（管理员）
    @RequireAdmin
    @PostMapping
    public Result addCategory(@RequestBody @Validated(Category.AddCategory.class) Category category) {
        categoryService.addCategory(category);
        return Result.success();
    }

    //文章分类列表（所有用户可见）
    @GetMapping
    public Result<List<Category>> List() {
        return Result.success(categoryService.List());
    }

    //获取文章分类详情
    @GetMapping("/detail")
    public Result<Category> detail(@RequestParam Integer id) {
        Category c = categoryService.findById(id);
        return Result.success(c);
    }

    //更新文章分类（管理员）
    @RequireAdmin
    @PutMapping("/update")
    public Result updateCategory(@RequestBody @Validated(Category.UpdateCategory.class) Category category) {
        categoryService.updateCategory(category);
        return Result.success();
    }

    //删除文章分类（管理员）
    @RequireAdmin
    @DeleteMapping("/delete")
    public Result deleteCategory(@RequestParam Integer id) {
        categoryService.deleteCategory(id);
        return Result.success();
    }
}
