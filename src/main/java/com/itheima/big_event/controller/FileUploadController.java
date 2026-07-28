package com.itheima.big_event.controller;

import com.itheima.big_event.pojo.Result;
import com.itheima.big_event.utils.FNossUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

@RestController
public class FileUploadController {
    @Autowired
    private FNossUtil fnossUtil;
    /**
     * 上传文件
     * MultipartFile file 文件对象专门接受前端上传的文件
     * transferTo() 方法把文件内容写入到指定的文件中
     *
     */
    @PostMapping("/upload")
    public Result<String> upload(MultipartFile file) throws IOException {
        //把文件内容存储到本地磁盘
        String fileName = file.getOriginalFilename();
        //保证文件名字唯一（防止覆盖
        //fileName.substring(fileName.lastIndexOf(".")) 获取文件扩展名
        //UUID.randomUUID().toString() 生成随机字符串
        String key = UUID.randomUUID().toString()+fileName.substring(fileName.lastIndexOf("."));
        String url = fnossUtil.uploadFile(key, file.getInputStream());
        return Result.success(url);
    }
}
