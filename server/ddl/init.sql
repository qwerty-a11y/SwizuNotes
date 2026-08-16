-- SwizuNotes 数据库初始化脚本（DDL 手动维护，非 ddl-auto）
-- 用法：psql -U postgres -h localhost -d swizunotes -f init.sql
-- 注意：重复执行会失败，幂等处理需按需修改

-- ============ 枚举类型 ============

CREATE TYPE article_status AS ENUM ('draft', 'published');
CREATE TYPE media_category AS ENUM ('image', 'video', 'audio', 'file');

-- ============ 序列（Hibernate GenerationType.AUTO 默认序列名 = 表名 + _seq，步长 50）============

CREATE SEQUENCE users_seq INCREMENT BY 50;
CREATE SEQUENCE articles_seq INCREMENT BY 50;

-- ============ 表 ============

CREATE TABLE users (
    id         INTEGER PRIMARY KEY DEFAULT nextval('users_seq'),
    account    VARCHAR(20)  NOT NULL,
    username   VARCHAR(50),
    password   VARCHAR(128) NOT NULL,  -- BCrypt
    is_admin   BOOLEAN      DEFAULT FALSE
);

CREATE TABLE articles (
    id           INTEGER PRIMARY KEY DEFAULT nextval('articles_seq'),
    author_id    INTEGER       NOT NULL REFERENCES users (id),
    title        VARCHAR(50)   NOT NULL,
    cover        VARCHAR(64),                       -- 封面图 media.id
    content      JSONB         NOT NULL,            -- ArticleContent {body, mediaRefs}
    summary      VARCHAR(50),
    publish_time TIMESTAMPTZ,
    modify_time  TIMESTAMPTZ,
    status       article_status NOT NULL
);
CREATE INDEX idx_articles_author ON articles (author_id);

CREATE TABLE media (
    id         VARCHAR(32)   PRIMARY KEY,           -- UUID32，即文件名
    article_id INTEGER       NOT NULL REFERENCES articles (id),
    type       media_category NOT NULL,
    mime_type  VARCHAR(128),
    metadata   JSONB
);
CREATE INDEX idx_media_article ON media (article_id);

CREATE TABLE static_resources (
    id   VARCHAR(255) PRIMARY KEY,                  -- 文件名
    path VARCHAR(255)                               -- 磁盘路径，管理员手动配置
);

CREATE TABLE user_avatars (
    id        VARCHAR(32) PRIMARY KEY,              -- UUID32
    user_id   INTEGER UNIQUE NOT NULL REFERENCES users (id),
    mime_type VARCHAR(128)
);

-- 主题配置（纯配置，不含文件路径；主题的 CSS/亮暗 banner 文件存 static_resources 表，
-- id = 文件名 <name>.css / <name>-light.<ext> / <name>-dark.<ext>，磁盘路径 uploads/themes/）
-- 状态三态：unpublished（未发布，完全不可访问）/ prerelease（预发布，日期设定生效，
-- 生效前按未发布处理，首次生效自动转 published）/ published（已发布，可随时切换）
CREATE TABLE themes (
    id           VARCHAR(64)  PRIMARY KEY,           -- 主题名（URL 标识，如 orange）
    display_name VARCHAR(50)  NOT NULL,              -- 显示名
    start_date   DATE,                               -- 公历自动切换开始日期（含，可空）
    end_date     DATE,                               -- 公历自动切换结束日期（含，可空）
    lunar_start  VARCHAR(10),                        -- 农历自动切换开始（M-d 或 闰M-d，如 8-15；每年重复，可空）
    lunar_end    VARCHAR(10),                        -- 农历自动切换结束（可空；无开始则单日生效）
    status       VARCHAR(16)  NOT NULL DEFAULT 'unpublished',  -- 发布状态
    created_at   TIMESTAMPTZ  DEFAULT now()
);

-- ============ 初始账号 ============
-- admin / 123456（BCrypt，经 pgcrypto 生成，兼容 Spring Security BCryptPasswordEncoder）

CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO users (account, username, password, is_admin)
VALUES ('admin', 'admin', crypt('123456', gen_salt('bf')), TRUE);
