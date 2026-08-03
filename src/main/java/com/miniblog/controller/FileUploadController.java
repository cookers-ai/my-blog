package com.miniblog.controller;

import com.miniblog.pojo.Result;
import com.miniblog.utils.FNossUtil;
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
    @PostMapping("/upload")
    public Result<String> upload(MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename();
        String key = UUID.randomUUID().toString() + fileName.substring(fileName.lastIndexOf("."));
        String url = fnossUtil.uploadFile(key, file.getInputStream());
        return Result.success(url);
    }
}
