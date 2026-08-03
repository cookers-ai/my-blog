package com.miniblog.utils;

import com.google.gson.Gson;
import com.miniblog.config.OssConfig;
import com.qiniu.common.QiniuException;
import com.qiniu.http.Response;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.Region;
import com.qiniu.storage.UploadManager;
import com.qiniu.storage.model.DefaultPutRet;
import com.qiniu.util.Auth;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
public class FNossUtil {

    @Autowired
    private OssConfig ossConfig;

    /**
     * 上传文件到七牛云
     * @param key             上传到七牛云的文件名
     * @param fileInputStream 文件输入流
     * @return 上传后的文件URL
     */
    public String uploadFile(String key, InputStream fileInputStream) {
        Configuration cfg = Configuration.create(Region.region2());
        cfg.resumableUploadAPIVersion = Configuration.ResumableUploadAPIVersion.V2;
        UploadManager uploadManager = new UploadManager(cfg);

        Auth auth = Auth.create(ossConfig.getAccessKey(), ossConfig.getSecretKey());
        String upToken = auth.uploadToken(ossConfig.getBucket());

        try {
            Response response = uploadManager.put(fileInputStream, key, upToken, null, null);
            DefaultPutRet putRet = new Gson().fromJson(response.bodyString(), DefaultPutRet.class);
            return ossConfig.getDomain() + "/" + putRet.key;
        } catch (QiniuException e) {
            throw new RuntimeException("七牛云上传失败", e);
        }
    }
}
