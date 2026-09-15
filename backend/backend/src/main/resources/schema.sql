-- ============================================
-- 棋牌竞技平台 - MySQL数据库初始化脚本
-- 启动时 Spring Boot 自动执行（幂等，可重复执行）
-- ============================================

-- 用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    salt VARCHAR(20) NOT NULL,
    security_code VARCHAR(50) NOT NULL,
    nickname VARCHAR(50),
    role INT NOT NULL DEFAULT 1 COMMENT '1-玩家 2-二级代理 3-总代理 4-客服 5-超管 6-一级代理',
    credits BIGINT NOT NULL DEFAULT 0 COMMENT '筹码积分/信用分',
    avatar TEXT,
    invite_code VARCHAR(10) UNIQUE COMMENT '自身邀请码',
    parent_id BIGINT COMMENT '上级用户ID',
    parent_invite_code VARCHAR(10),
    has_fee_failure TINYINT(1) DEFAULT 0 COMMENT '代理是否存在扣费失败',
    last_login_ip VARCHAR(50),
    last_login_time DATETIME,
    status INT DEFAULT 0 COMMENT '0-正常 1-禁用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 房间表
CREATE TABLE IF NOT EXISTS game_room (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_no VARCHAR(10) NOT NULL UNIQUE,
    room_password VARCHAR(50),
    game_type VARCHAR(30) NOT NULL,
    room_level VARCHAR(20) NOT NULL,
    room_name VARCHAR(100),
    owner_id BIGINT NOT NULL,
    owner_invite_code VARCHAR(10),
    max_players INT DEFAULT 8,
    min_buyin INT NOT NULL,
    max_buyin INT NOT NULL,
    status INT DEFAULT 0 COMMENT '0-等待中 1-对局中 2-已结算 3-已关闭',
    current_round INT DEFAULT 0,
    total_rounds INT DEFAULT 25,
    total_turnover BIGINT DEFAULT 0,
    total_rake BIGINT DEFAULT 0,
    total_water_fee BIGINT DEFAULT 0,
    fixed_bet_amount BIGINT DEFAULT 100,
    is_public_match TINYINT(1) DEFAULT 0 COMMENT '是否公域匹配房',
    match_agent_id BIGINT COMMENT '公域匹配房归属代理ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='游戏房间表';

-- 房间玩家
CREATE TABLE IF NOT EXISTS room_player (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id BIGINT NOT NULL,
    room_no VARCHAR(10),
    user_id BIGINT NOT NULL,
    username VARCHAR(50),
    nickname VARCHAR(50),
    seat_no INT DEFAULT -1,
    is_observer TINYINT(1) DEFAULT 0,
    buyin_credits BIGINT DEFAULT 0,
    current_credits BIGINT DEFAULT 0,
    total_profit BIGINT DEFAULT 0,
    is_auto_play TINYINT(1) DEFAULT 0,
    is_looked TINYINT(1) DEFAULT 0,
    is_ready TINYINT(1) DEFAULT 0 COMMENT '是否已准备（原型30-等待开局）',
    join_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    leave_time DATETIME,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='房间玩家表';

-- 对局记录
CREATE TABLE IF NOT EXISTS game_round (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id BIGINT NOT NULL,
    room_no VARCHAR(10),
    round_no INT NOT NULL,
    round_turnover BIGINT DEFAULT 0,
    round_rake BIGINT DEFAULT 0,
    banker_id BIGINT,
    winner_ids VARCHAR(500),
    loser_ids VARCHAR(500),
    settlement_detail LONGTEXT,
    deal_record LONGTEXT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对局记录表';

-- 积分流水
CREATE TABLE IF NOT EXISTS credit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    username VARCHAR(50),
    change_type INT NOT NULL,
    change_value BIGINT NOT NULL,
    before_value BIGINT,
    after_value BIGINT,
    room_id BIGINT,
    room_no VARCHAR(10),
    round_id BIGINT,
    operator_id BIGINT,
    operator_name VARCHAR(50),
    remark VARCHAR(500),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分流水表';

-- 代理水费扣费记录
CREATE TABLE IF NOT EXISTS agent_water_fee_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_id BIGINT NOT NULL,
    agent_name VARCHAR(50),
    room_id BIGINT,
    room_no VARCHAR(10),
    room_turnover BIGINT DEFAULT 0,
    fee_amount BIGINT NOT NULL,
    before_credit BIGINT,
    after_credit BIGINT,
    status INT DEFAULT 0 COMMENT '0-成功 1-失败',
    fail_reason VARCHAR(255),
    repaid TINYINT(1) DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代理水费扣费记录表';

-- 总代理分润
CREATE TABLE IF NOT EXISTS general_agent_commission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    general_agent_id BIGINT NOT NULL,
    general_agent_name VARCHAR(50),
    source_agent_id BIGINT,
    source_agent_name VARCHAR(50),
    room_id BIGINT,
    room_no VARCHAR(10),
    room_turnover BIGINT DEFAULT 0,
    commission_amount BIGINT NOT NULL,
    before_credit BIGINT,
    after_credit BIGINT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='总代理分润表';

-- 返佣历史账单（房主扣3%等值 + 系统返1%）
CREATE TABLE IF NOT EXISTS rake_rebate_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id BIGINT NOT NULL COMMENT '房主ID（总代理或小代理）',
    owner_name VARCHAR(50),
    owner_role INT COMMENT '房主角色 2=小代理 3=总代理',
    room_id BIGINT,
    room_no VARCHAR(10) COMMENT '房间号（账单展示核心字段）',
    room_turnover BIGINT DEFAULT 0 COMMENT '总流水',
    total_rake BIGINT DEFAULT 0 COMMENT '房间总抽水',
    deducted_amount BIGINT DEFAULT 0 COMMENT '扣除多少（总流水3%）',
    rebate_amount BIGINT DEFAULT 0 COMMENT '系统返还信用分（总流水1%）',
    net_cost BIGINT DEFAULT 0 COMMENT '房主净成本=扣-返',
    before_credit BIGINT,
    after_credit BIGINT,
    status INT DEFAULT 0 COMMENT '0=成功 1=失败(信用分不足)',
    fail_reason VARCHAR(255),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='返佣账单表';

-- 平台公告
CREATE TABLE IF NOT EXISTS platform_announcement (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200),
    content LONGTEXT,
    top_flag TINYINT(1) DEFAULT 0,
    publisher_id BIGINT,
    publisher_name VARCHAR(50),
    status INT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台公告表';

-- 平台投诉
CREATE TABLE IF NOT EXISTS platform_complaint (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    username VARCHAR(50),
    complaint_type INT DEFAULT 5,
    title VARCHAR(200),
    content LONGTEXT,
    images VARCHAR(500),
    status INT DEFAULT 0,
    handler_id BIGINT,
    handler_name VARCHAR(50),
    reply LONGTEXT,
    handle_time DATETIME,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台投诉表';

-- ============================================
-- 用户消息通知表（原型 20-消息通知页）
-- msg_type: 1-系统 2-佣金 3-投诉 4-充值 5-活动
-- ============================================
CREATE TABLE IF NOT EXISTS user_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '接收者用户ID',
    msg_type INT NOT NULL DEFAULT 1 COMMENT '1-系统 2-佣金 3-投诉 4-充值 5-活动',
    title VARCHAR(200),
    content VARCHAR(1000),
    biz_id BIGINT COMMENT '关联业务ID（投诉单/提现单等）',
    read_flag TINYINT(1) DEFAULT 0 COMMENT '0-未读 1-已读',
    read_time DATETIME,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户消息通知表';

-- ============================================
-- 充值订单表（原型 08-充值页）
-- status: 0-待支付 1-已支付 2-已取消 3-已失败
-- ============================================
CREATE TABLE IF NOT EXISTS recharge_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(40) NOT NULL UNIQUE COMMENT '订单号',
    user_id BIGINT NOT NULL,
    username VARCHAR(50),
    amount_yuan DECIMAL(10,2) NOT NULL COMMENT '充值金额（元）',
    gift_credits BIGINT DEFAULT 0 COMMENT '赠送游戏币',
    actual_credits BIGINT NOT NULL COMMENT '实际到账游戏币',
    pay_method VARCHAR(20) COMMENT 'ALIPAY/WECHAT/BANK',
    status INT DEFAULT 0 COMMENT '0-待支付 1-已支付 2-已取消 3-已失败',
    pay_time DATETIME,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='充值订单表';

-- ============================================
-- 提现订单表（原型 17-提现页）
-- status: 0-待处理 1-已到账 2-已驳回
-- ============================================
CREATE TABLE IF NOT EXISTS withdraw_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(40) NOT NULL UNIQUE COMMENT '订单号',
    user_id BIGINT NOT NULL,
    username VARCHAR(50),
    amount_yuan DECIMAL(10,2) NOT NULL COMMENT '提现金额（元）',
    deduct_credits BIGINT NOT NULL COMMENT '扣减游戏币',
    withdraw_method VARCHAR(20) COMMENT 'ALIPAY/WECHAT/BANK',
    account VARCHAR(120) COMMENT '收款账号',
    status INT DEFAULT 0 COMMENT '0-待处理 1-已到账 2-已驳回',
    fail_reason VARCHAR(255),
    audit_id BIGINT COMMENT '审核人ID',
    audit_name VARCHAR(50),
    audit_time DATETIME,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提现订单表';

-- ============================================
-- 用户设置表（原型 23-设置页 / 45-牌桌设置面板）
-- ============================================
CREATE TABLE IF NOT EXISTS user_setting (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    sound_enabled TINYINT(1) DEFAULT 1 COMMENT '音效',
    music_enabled TINYINT(1) DEFAULT 1 COMMENT '背景音乐',
    vibrate_enabled TINYINT(1) DEFAULT 0 COMMENT '震动',
    auto_play_enabled TINYINT(1) DEFAULT 0 COMMENT '自动挂机',
    quick_bet_enabled TINYINT(1) DEFAULT 0 COMMENT '显示快捷下注按钮',
    language VARCHAR(20) DEFAULT 'zh-CN',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户设置表';

-- ============================================
-- 常见问题表（原型 21-客服投诉页）
-- ============================================
CREATE TABLE IF NOT EXISTS faq_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    question VARCHAR(300) NOT NULL,
    answer LONGTEXT,
    sort_no INT DEFAULT 0,
    status INT DEFAULT 1 COMMENT '1-启用 0-停用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='常见问题表';

-- ============================================
-- 在线玩家快照（原型 04/11 大厅左侧在线玩家列表）
-- ============================================
CREATE TABLE IF NOT EXISTS online_player (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    nickname VARCHAR(50),
    avatar TEXT,
    credits BIGINT DEFAULT 0,
    game_type VARCHAR(30) COMMENT '所在游戏/房间玩法',
    room_no VARCHAR(10),
    last_active_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='在线玩家快照表';

-- ============================================
-- 初始化账号数据（幂等：已存在则跳过）
-- 密码均为 123456，salt=abcdefgh
-- md5(md5("123456")+"abcdefgh") = c312d1267e2fcdcb96f1862eca876775
-- ============================================
INSERT IGNORE INTO sys_user (id, username, password, salt, security_code, nickname, role, credits, invite_code, has_fee_failure, status)
    VALUES (1, 'admin', 'c312d1267e2fcdcb96f1862eca876775', 'abcdefgh', '888888', '超级管理员', 5, 999999, 'SUPER1', 0, 0);
INSERT IGNORE INTO sys_user (id, username, password, salt, security_code, nickname, role, credits, invite_code, has_fee_failure, status)
    VALUES (2, 'kefu', 'c312d1267e2fcdcb96f1862eca876775', 'abcdefgh', '888888', '客服001', 4, 0, 'CUSTO1', 0, 0);
INSERT IGNORE INTO sys_user (id, username, password, salt, security_code, nickname, role, credits, invite_code, parent_id, parent_invite_code, has_fee_failure, status)
    VALUES (3, 'zongdai', 'c312d1267e2fcdcb96f1862eca876775', 'abcdefgh', '888888', '总代理老王', 3, 50000, 'ZONGD1', 1, 'SUPER1', 0, 0);
INSERT IGNORE INTO sys_user (id, username, password, salt, security_code, nickname, role, credits, invite_code, parent_id, parent_invite_code, has_fee_failure, status)
    VALUES (10, 'hexindaili', 'c312d1267e2fcdcb96f1862eca876775', 'abcdefgh', '888888', '核心代理老周', 6, 30000, 'CORED1', 3, 'ZONGD1', 0, 0);
INSERT IGNORE INTO sys_user (id, username, password, salt, security_code, nickname, role, credits, invite_code, parent_id, parent_invite_code, has_fee_failure, status)
    VALUES (4, 'daili01', 'c312d1267e2fcdcb96f1862eca876775', 'abcdefgh', '888888', '代理小张', 2, 20000, 'AGENT1', 10, 'CORED1', 0, 0);
INSERT IGNORE INTO sys_user (id, username, password, salt, security_code, nickname, role, credits, invite_code, parent_id, parent_invite_code, has_fee_failure, status)
    VALUES (5, 'daili02', 'c312d1267e2fcdcb96f1862eca876775', 'abcdefgh', '888888', '代理小李', 2, 10000, 'AGENT2', 10, 'CORED1', 0, 0);
INSERT IGNORE INTO sys_user (id, username, password, salt, security_code, nickname, role, credits, invite_code, parent_id, parent_invite_code, has_fee_failure, status)
    VALUES (6, 'player1', 'c312d1267e2fcdcb96f1862eca876775', 'abcdefgh', '888888', '玩家小明', 1, 5000, 'PLAYE1', 4, 'AGENT1', 0, 0);
INSERT IGNORE INTO sys_user (id, username, password, salt, security_code, nickname, role, credits, invite_code, parent_id, parent_invite_code, has_fee_failure, status)
    VALUES (7, 'player2', 'c312d1267e2fcdcb96f1862eca876775', 'abcdefgh', '888888', '玩家小红', 1, 3000, 'PLAYE2', 4, 'AGENT1', 0, 0);
INSERT IGNORE INTO sys_user (id, username, password, salt, security_code, nickname, role, credits, invite_code, parent_id, parent_invite_code, has_fee_failure, status)
    VALUES (8, 'player3', 'c312d1267e2fcdcb96f1862eca876775', 'abcdefgh', '888888', '玩家小刚', 1, 8000, 'PLAYE3', 5, 'AGENT2', 0, 0);
INSERT IGNORE INTO sys_user (id, username, password, salt, security_code, nickname, role, credits, invite_code, parent_id, parent_invite_code, has_fee_failure, status)
    VALUES (9, 'player4', 'c312d1267e2fcdcb96f1862eca876775', 'abcdefgh', '888888', '玩家小莉', 1, 2000, 'PLAYE4', 5, 'AGENT2', 0, 0);

-- 初始化公告
INSERT IGNORE INTO platform_announcement (id, title, content, top_flag, publisher_id, publisher_name, status)
    VALUES (1, '平台公测启动公告', '欢迎参与棋牌竞技平台公测！所有游戏均为竞技积分模式，无任何现金交易功能。测试账号密码均为 123456，安全码 888888。', 1, 1, '超级管理员', 1);
INSERT IGNORE INTO platform_announcement (id, title, content, top_flag, publisher_id, publisher_name, status)
    VALUES (2, '欢迎来到V-POKER 新用户注册送百万金币', '新用户注册即可领取百万金币福利，邀请好友还能获得额外奖励，快来开始你的第一局吧！', 1, 1, '超级管理员', 1);

-- 初始化常见问题（原型 21-客服投诉页）
INSERT IGNORE INTO faq_item (id, question, answer, sort_no, status) VALUES
    (1, '如何充值?', '点击「个人中心 — 充值」，选择充值档位与支付方式，确认后即可完成充值，金币实时到账。', 1, 1);
INSERT IGNORE INTO faq_item (id, question, answer, sort_no, status) VALUES
    (2, '如何提现?', '点击「个人中心 — 提现」，输入提现金额并选择收款方式，提交申请后由客服审核，审核通过后 1-3 个工作日到账。', 2, 1);
INSERT IGNORE INTO faq_item (id, question, answer, sort_no, status) VALUES
    (3, '账号被冻结怎么办?', '账号被冻结通常因异常操作触发风控。请点击「联系客服 — 发起对话」提交申诉，客服将在 24 小时内处理。', 3, 1);
INSERT IGNORE INTO faq_item (id, question, answer, sort_no, status) VALUES
    (4, '游戏币可以转赠给好友吗?', '可以。玩家之间可通过「赠送游戏币」功能互相转赠；代理可向下级玩家划拨游戏币。', 4, 1);
INSERT IGNORE INTO faq_item (id, question, answer, sort_no, status) VALUES
    (5, '房间中途退出会怎样?', '退出房间后本局将按弃牌处理，带入的剩余金币会自动退回账户。', 5, 1);

-- 初始化平台用户设置（幂等）
INSERT IGNORE INTO user_setting (user_id, sound_enabled, music_enabled, vibrate_enabled, auto_play_enabled, quick_bet_enabled, language)
    SELECT id, 1, 1, 0, 0, 0, 'zh-CN' FROM sys_user;
