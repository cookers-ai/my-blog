-- 删表（按外键依赖倒序）
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS follow;
DROP TABLE IF EXISTS like_record;
DROP TABLE IF EXISTS comment;
DROP TABLE IF EXISTS operation_log;
DROP TABLE IF EXISTS article;
DROP TABLE IF EXISTS article_category;
DROP TABLE IF EXISTS category;
DROP TABLE IF EXISTS `user`;
SET FOREIGN_KEY_CHECKS = 1;

-- 用户表
create table user (
    id int unsigned primary key auto_increment comment 'ID',
    username varchar(20) not null unique comment '用户名',
    password varchar(255) comment '密码',
    nickname varchar(10) default '' comment '昵称',
    email varchar(128) default '' comment '邮箱',
    user_pic varchar(512) default '' comment '头像',
    create_time datetime not null comment '创建时间',
    update_time datetime not null comment '修改时间'
) comment '用户表';

ALTER TABLE `user` ADD COLUMN `role` VARCHAR(20) NOT NULL DEFAULT 'user';

ALTER TABLE user
    ADD COLUMN follow_count INT UNSIGNED DEFAULT 0 COMMENT '关注数',
    ADD COLUMN fans_count INT UNSIGNED DEFAULT 0 COMMENT '粉丝数';

ALTER TABLE `user` ADD COLUMN user_status tinyint default 0 comment '账号状态 0正常 1禁用';

ALTER TABLE user ADD COLUMN bio VARCHAR(200) DEFAULT NULL COMMENT '个人简介';

-- 分类表
create table category(
    id int unsigned primary key auto_increment comment 'ID',
    category_name varchar(32) not null comment '分类名称',
    category_alias varchar(32) not null comment '分类别名',
    create_user int unsigned default null comment '创建人ID',
    create_time datetime not null comment '创建时间',
    update_time datetime not null comment '修改时间',
    constraint fk_category_user foreign key (create_user) references user(id)
);

ALTER TABLE category
    MODIFY COLUMN category_alias VARCHAR(100) NOT NULL DEFAULT '' COMMENT '分类别名';

-- 文章表
create table article(
    id int unsigned primary key auto_increment comment 'ID',
    title varchar(100) not null comment '文章标题',
    content MEDIUMTEXT not null comment '文章内容',
    cover_img varchar(512) not null comment '文章封面',
    state varchar(3) default '草稿' comment '文章状态: 只能是[已发布] 或者 [草稿]',
    category_id int unsigned comment '文章分类ID',
    create_user int unsigned not null comment '创建人ID',
    create_time datetime not null comment '创建时间',
    update_time datetime not null comment '修改时间',
    constraint fk_article_user foreign key (create_user) references user(id)
);

ALTER TABLE article ADD COLUMN view_count INT DEFAULT 0 COMMENT '浏览量' AFTER create_user;
ALTER TABLE article ADD COLUMN like_count INT DEFAULT 0 COMMENT '点赞数' AFTER view_count;
ALTER TABLE article ADD COLUMN comment_count INT DEFAULT 0 COMMENT '评论数' AFTER like_count;

ALTER TABLE article MODIFY COLUMN create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';
ALTER TABLE article MODIFY COLUMN update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间';

-- 操作日志表
CREATE TABLE `operation_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint DEFAULT NULL COMMENT '操作人ID',
  `username` varchar(50) DEFAULT NULL COMMENT '操作人用户名',
  `module` varchar(50) DEFAULT NULL COMMENT '操作模块（如：用户管理、文章管理）',
  `operation` varchar(50) NOT NULL COMMENT '操作类型（如：封禁用户、下架文章）',
  `method` varchar(200) DEFAULT NULL COMMENT '请求方法（类名.方法名）',
  `params` text COMMENT '请求参数（JSON）',
  `ip` varchar(50) DEFAULT NULL COMMENT '操作人IP',
  `status` tinyint DEFAULT 1 COMMENT '操作状态（1 成功，0 失败）',
  `error_msg` varchar(500) DEFAULT NULL COMMENT '错误信息',
  `cost_time` bigint DEFAULT NULL COMMENT '耗时（毫秒）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';

-- 点赞记录表
DROP TABLE IF EXISTS like_record;
CREATE TABLE like_record (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '点赞记录ID',
    user_id INT UNSIGNED NOT NULL COMMENT '点赞用户ID',
    article_id INT UNSIGNED NOT NULL COMMENT '被点赞文章ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
    UNIQUE KEY uk_user_article (user_id, article_id),
    CONSTRAINT fk_like_user FOREIGN KEY (user_id) REFERENCES user(id),
    CONSTRAINT fk_like_article FOREIGN KEY (article_id) REFERENCES article(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '点赞记录表';

-- 评论表
DROP TABLE IF EXISTS comment;
CREATE TABLE comment (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '评论ID',
    user_id INT UNSIGNED NOT NULL COMMENT '评论者ID',
    article_id INT UNSIGNED NOT NULL COMMENT '文章ID',
    content VARCHAR(500) NOT NULL COMMENT '评论内容',
    parent_id BIGINT UNSIGNED DEFAULT 0 COMMENT '父评论ID，0表示根评论',
    reply_user_id INT UNSIGNED DEFAULT NULL COMMENT '回复的用户ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    CONSTRAINT fk_comment_user FOREIGN KEY (user_id) REFERENCES user(id),
    CONSTRAINT fk_comment_article FOREIGN KEY (article_id) REFERENCES article(id),
    CONSTRAINT fk_comment_reply_user FOREIGN KEY (reply_user_id) REFERENCES user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT '评论表';

-- 关注表
DROP TABLE IF EXISTS follow;
CREATE TABLE follow (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '关注记录ID',
    follower_id INT UNSIGNED NOT NULL COMMENT '关注者ID（粉丝）',
    followee_id INT UNSIGNED NOT NULL COMMENT '被关注者ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '关注时间',
    UNIQUE KEY uk_follower_followee (follower_id, followee_id),
    KEY idx_follower_id (follower_id),
    KEY idx_followee_id (followee_id),
    CONSTRAINT fk_follow_follower FOREIGN KEY (follower_id) REFERENCES user(id),
    CONSTRAINT fk_follow_followee FOREIGN KEY (followee_id) REFERENCES user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='关注记录表';

-- ==================== 种子数据 ====================

-- 管理员
INSERT INTO user (username, password, nickname, role, user_status)
VALUES ('admin', 'admin123', '管理员', 'admin', 0);

-- 分类（create_user = NULL = 全局分类）
TRUNCATE TABLE category;
INSERT INTO category (id, category_name, create_user, create_time, update_time)
VALUES
(1,  '军事', NULL, NOW(), NOW()),
(2,  '生物', NULL, NOW(), NOW()),
(3,  '影视', NULL, NOW(), NOW()),
(4,  '科技', NULL, NOW(), NOW()),
(5,  '体育', NULL, NOW(), NOW()),
(6,  '娱乐', NULL, NOW(), NOW()),
(7,  '生活', NULL, NOW(), NOW()),
(8,  '学习', NULL, NOW(), NOW()),
(9,  '职场', NULL, NOW(), NOW()),
(10, '旅游', NULL, NOW(), NOW()),
(11, '美食', NULL, NOW(), NOW()),
(12, '游戏', NULL, NOW(), NOW()),
(13, '财经', NULL, NOW(), NOW()),
(14, '历史', NULL, NOW(), NOW()),
(15, '地理', NULL, NOW(), NOW()),
(16, '其他', NULL, NOW(), NOW());
