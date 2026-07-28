package com.itheima.big_event.task;


import com.itheima.big_event.mapper.ArticleMapper;
import com.itheima.big_event.utils.RedisConstants;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;

@Slf4j
@Component
public class ViewCountSyncTask {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private ArticleMapper articleMapper;

    // 每 10 分钟执行一次
    @Scheduled(cron = "0 */10 * * * ?")
    public void syncViewCount() {
        // 1. 使用 SCAN 扫描 Redis 中所有文章的计数缓存 key（避免 keys() 阻塞）
        Set<String> keys = new HashSet<>();
        try {
            Cursor<String> cursor = stringRedisTemplate.scan(
                    ScanOptions.scanOptions()
                            .match(RedisConstants.ARTICLE_DETAIL_COUNT_KEY + "*")
                            .count(100)
                            .build());
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
            cursor.close();
        } catch (Exception e) {
            // SCAN 失败时降级使用 keys
            keys = stringRedisTemplate.keys(RedisConstants.ARTICLE_DETAIL_COUNT_KEY + "*");
        }

        if (keys == null || keys.isEmpty()) {
            log.info("没有需要同步的浏览量数据");
            return;
        }

        int success = 0;
        int prefixLen = RedisConstants.ARTICLE_DETAIL_COUNT_KEY.length();

        // 2. 遍历每个 key，提取浏览量并同步到 MySQL
        for (String key : keys) {
            try {
                // 从 key 中切出文章 ID（"articleDetail:count:1" → "1" → 1）
                String idStr = key.substring(prefixLen);
                int articleId = Integer.parseInt(idStr);

                // 从 Redis Hash 读取 viewCount 字段
                Object viewObj = stringRedisTemplate.opsForHash().get(key, "viewCount");
                if (viewObj == null) {
                    continue;
                }
                int viewCount = Integer.parseInt(viewObj.toString());

                // 写回 MySQL
                articleMapper.updateViewCount(articleId, viewCount);
                success++;
            } catch (Exception e) {
                log.error("同步浏览量失败, key={}", key, e);
            }
        }

        log.info("浏览量同步完成 — 成功: {}, 总数: {}", success, keys.size());
    }
}
