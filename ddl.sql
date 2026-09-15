-- =====================================================================
-- 棋牌竞技平台 · 数据库 DDL
-- 数据库：MySQL 8.0      库名：poker_platform      字符集：utf8mb4
--
-- 说明：
--   1. 本文件与后端 src/main/resources/schema.sql 结构完全一致，
--      已去除 Spring Boot 初始化用的 INSERT 种子数据，仅保留建表语句。
--   2. 全部使用 CREATE TABLE IF NOT EXISTS，可重复执行（幂等）。
--   3. 严禁在本文件中使用 DELIMITER / 存储过程 / 触发器：
--      Spring 的 ScriptUtils 按分号切分脚本，遇到 DELIMITER 会直接报
--      SQLSyntaxErrorException 导致应用启动失败。
-- =====================================================================

CREATE DATABASE IF NOT EXISTS poker_platform
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;

USE poker_platform;

-- =====================================================================
-- 1. 用户表
-- role: 1-玩家 2-二级代理 3-总代理 4-客服 5-超管 6-一级代理
-- 代理层级链：超管 -> 总代(3) -> 一级(6) -> 二级(2) -> 玩家(1)
-- =====================================================================
CREATE TABLE IF NOT EXISTS sys_user (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    username          VARCHAR(50)  NOT NULL UNIQUE              COMMENT '登录账号',
    password          VARCHAR(100) NOT NULL                     COMMENT '密码 MD5(MD5(pwd)+salt)',
    salt              VARCHAR(20)  NOT NULL                     COMMENT '密码盐',
    security_code     VARCHAR(50)  NOT NULL                     COMMENT '安全码（独立加密，不可与登录密码相同）',
    nickname          VARCHAR(50)                               COMMENT '昵称',
    role              INT          NOT NULL DEFAULT 1           COMMENT '1-玩家 2-二级代理 3-总代理 4-客服 5-超管 6-一级代理',
    credits           BIGINT       NOT NULL DEFAULT 0           COMMENT '筹码积分/信用分',
    avatar            TEXT                                      COMMENT '头像 URL',
    invite_code       VARCHAR(10)  UNIQUE                       COMMENT '自身邀请码',
    parent_id         BIGINT                                    COMMENT '上级用户ID',
    parent_invite_code VARCHAR(10)                              COMMENT '上级邀请码',
    has_fee_failure   TINYINT(1)   DEFAULT 0                    COMMENT '代理是否存在扣费失败（1=欠费禁开房）',
    last_login_ip     VARCHAR(50)                               COMMENT '最近登录 IP',
    last_login_time   DATETIME                                  COMMENT '最近登录时间',
    status            INT          DEFAULT 0                    COMMENT '0-正常 1-禁用',
    create_time       DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME     DEFAULT CURRENT_TIMESTAMP,
    deleted           INT          DEFAULT 0                    COMMENT '逻辑删除 0-未删 1-已删',
    KEY idx_user_parent   (parent_id),
    KEY idx_user_role     (role),
    KEY idx_user_invite   (invite_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- =====================================================================
-- 2. 游戏房间表
-- game_type:  TEXAS / JINHUA / SANGONG / DOUNIU / TONGBI_NIUNIU / TONGBI_SANGONG
-- room_level: PRIMARY(初级) / ADVANCED(高级) / PREMIUM(顶级)
-- status:     0-等待中 1-对局中 2-已结算 3-已关闭
-- =====================================================================
CREATE TABLE IF NOT EXISTS game_room (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_no           VARCHAR(10)  NOT NULL UNIQUE              COMMENT '房间号（6 位）',
    room_password     VARCHAR(50)                               COMMENT '房间密码（平台房为空）',
    game_type         VARCHAR(30)  NOT NULL                     COMMENT '游戏玩法 code',
    room_level        VARCHAR(20)  NOT NULL                     COMMENT '房间等级 code',
    room_name         VARCHAR(100)                              COMMENT '房间名称',
    owner_id          BIGINT       NOT NULL                     COMMENT '房主用户ID（代理）',
    owner_invite_code VARCHAR(10)                               COMMENT '房主邀请码',
    max_players       INT          DEFAULT 8                    COMMENT '人数上限',
    min_buyin         INT          NOT NULL                     COMMENT '最低带入筹码',
    max_buyin         INT          NOT NULL                     COMMENT '最高带入筹码',
    status            INT          DEFAULT 0                    COMMENT '0-等待中 1-对局中 2-已结算 3-已关闭',
    current_round     INT          DEFAULT 0                    COMMENT '当前局数',
    total_rounds      INT          DEFAULT 25                   COMMENT '总局数',
    total_turnover    BIGINT       DEFAULT 0                    COMMENT '累计总流水',
    total_rake        BIGINT       DEFAULT 0                    COMMENT '累计平台抽水',
    total_water_fee   BIGINT       DEFAULT 0                    COMMENT '累计代理水费',
    fixed_bet_amount  BIGINT       DEFAULT 100                  COMMENT '统一下注额（通比类）',
    is_public_match   TINYINT(1)   DEFAULT 0                    COMMENT '是否公域匹配房',
    match_agent_id    BIGINT                                    COMMENT '公域匹配房归属代理ID',
    create_time       DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME     DEFAULT CURRENT_TIMESTAMP,
    deleted           INT          DEFAULT 0,
    KEY idx_room_owner  (owner_id),
    KEY idx_room_no     (room_no),
    KEY idx_room_status (status),
    KEY idx_room_type   (game_type, room_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='游戏房间表';

-- =====================================================================
-- 3. 房间玩家表
-- seat_no = -1 表示旁观者（代理建房后自动入座旁观位）
-- =====================================================================
CREATE TABLE IF NOT EXISTS room_player (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id         BIGINT       NOT NULL                        COMMENT '房间ID',
    room_no         VARCHAR(10)                                  COMMENT '房间号',
    user_id         BIGINT       NOT NULL                        COMMENT '玩家ID',
    username        VARCHAR(50)                                  COMMENT '账号',
    nickname        VARCHAR(50)                                  COMMENT '昵称快照',
    seat_no         INT          DEFAULT -1                      COMMENT '座位号，-1=旁观',
    is_observer     TINYINT(1)   DEFAULT 0                       COMMENT '是否旁观者',
    buyin_credits   BIGINT       DEFAULT 0                       COMMENT '带入筹码',
    current_credits BIGINT       DEFAULT 0                       COMMENT '当前剩余筹码',
    total_profit    BIGINT       DEFAULT 0                       COMMENT '本房间累计输赢',
    is_auto_play    TINYINT(1)   DEFAULT 0                       COMMENT '是否自动挂机（通比类）',
    is_looked       TINYINT(1)   DEFAULT 0                       COMMENT '金花是否已看牌（0=闷牌）',
    is_ready        TINYINT(1)   DEFAULT 0                       COMMENT '是否已准备（原型30-等待开局）',
    join_time       DATETIME     DEFAULT CURRENT_TIMESTAMP       COMMENT '入座时间',
    leave_time      DATETIME                                     COMMENT '离开时间',
    update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    deleted         INT          DEFAULT 0,
    KEY idx_rp_room (room_id),
    KEY idx_rp_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='房间玩家表';

-- =====================================================================
-- 4. 对局记录表
-- deal_record 仅用于审计，不下发前端底牌（防作弊）
-- =====================================================================
CREATE TABLE IF NOT EXISTS game_round (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id            BIGINT      NOT NULL                      COMMENT '房间ID',
    room_no            VARCHAR(10)                               COMMENT '房间号',
    round_no           INT         NOT NULL                      COMMENT '第几局',
    round_turnover     BIGINT      DEFAULT 0                     COMMENT '本局总流水',
    round_rake         BIGINT      DEFAULT 0                     COMMENT '本局平台抽水',
    banker_id          BIGINT                                    COMMENT '庄家用户ID（抢庄类）',
    winner_ids         VARCHAR(500)                              COMMENT '赢家ID列表（逗号分隔）',
    loser_ids          VARCHAR(500)                              COMMENT '输家ID列表（逗号分隔）',
    settlement_detail  LONGTEXT                                  COMMENT '本局结算明细 JSON',
    deal_record        LONGTEXT                                  COMMENT '发牌记录 JSON（仅审计）',
    create_time        DATETIME    DEFAULT CURRENT_TIMESTAMP,
    KEY idx_gr_room (room_id),
    KEY idx_gr_round (room_id, round_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对局记录表';

-- =====================================================================
-- 5. 积分流水表
-- change_type: 1-注册赠送 2-代理增减下线 3-对局输赢 4-平台抽水 5-代理水费扣费
--              6-总代理分润 7-客服人工调整 8-管理员调整 9-代理赠送玩家
--              10-补扣欠费 11-系统返佣 12-玩家之间赠送(P2P) 13-推广划拨 14-对局赠送
-- =====================================================================
CREATE TABLE IF NOT EXISTS credit_log (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT       NOT NULL                          COMMENT '归属用户ID',
    username      VARCHAR(50)                                    COMMENT '归属账号',
    change_type   INT          NOT NULL                          COMMENT '变动类型',
    change_value  BIGINT       NOT NULL                          COMMENT '变动金额（正加负减）',
    before_value  BIGINT                                         COMMENT '变动前余额',
    after_value   BIGINT                                         COMMENT '变动后余额',
    room_id       BIGINT                                         COMMENT '关联房间ID',
    room_no       VARCHAR(10)                                    COMMENT '关联房间号',
    round_id      BIGINT                                         COMMENT '关联对局ID',
    operator_id   BIGINT                                         COMMENT '操作人ID',
    operator_name VARCHAR(50)                                    COMMENT '操作人名称',
    remark        VARCHAR(500)                                   COMMENT '备注',
    create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    KEY idx_cl_user (user_id),
    KEY idx_cl_type (user_id, change_type),
    KEY idx_cl_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分流水表';

-- =====================================================================
-- 6. 代理水费扣费记录表
-- =====================================================================
CREATE TABLE IF NOT EXISTS agent_water_fee_log (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_id      BIGINT       NOT NULL                          COMMENT '代理用户ID',
    agent_name    VARCHAR(50)                                    COMMENT '代理名称',
    room_id       BIGINT                                         COMMENT '房间ID',
    room_no       VARCHAR(10)                                    COMMENT '房间号',
    room_turnover BIGINT       DEFAULT 0                         COMMENT '房间流水',
    fee_amount    BIGINT       NOT NULL                          COMMENT '扣费金额',
    before_credit BIGINT                                         COMMENT '扣费前余额',
    after_credit  BIGINT                                         COMMENT '扣费后余额',
    status        INT          DEFAULT 0                         COMMENT '0-成功 1-失败',
    fail_reason   VARCHAR(255)                                   COMMENT '失败原因',
    repaid        TINYINT(1)   DEFAULT 0                         COMMENT '是否已补缴',
    create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    KEY idx_awf_agent (agent_id),
    KEY idx_awf_room  (room_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代理水费扣费记录表';

-- =====================================================================
-- 7. 总代理分润表
-- =====================================================================
CREATE TABLE IF NOT EXISTS general_agent_commission (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    general_agent_id    BIGINT     NOT NULL                      COMMENT '总代理用户ID',
    general_agent_name  VARCHAR(50)                              COMMENT '总代理名称',
    source_agent_id     BIGINT                                   COMMENT '来源代理ID',
    source_agent_name   VARCHAR(50)                              COMMENT '来源代理名称',
    room_id             BIGINT                                   COMMENT '房间ID',
    room_no             VARCHAR(10)                              COMMENT '房间号',
    room_turnover       BIGINT     DEFAULT 0                     COMMENT '房间流水',
    commission_amount   BIGINT     NOT NULL                      COMMENT '分佣金额',
    before_credit       BIGINT                                   COMMENT '变动前余额',
    after_credit        BIGINT                                   COMMENT '变动后余额',
    create_time         DATETIME   DEFAULT CURRENT_TIMESTAMP,
    KEY idx_gac_ga (general_agent_id),
    KEY idx_gac_room (room_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='总代理分润表';

-- =====================================================================
-- 8. 返佣账单表
-- =====================================================================
CREATE TABLE IF NOT EXISTS rake_rebate_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_id        BIGINT       NOT NULL                        COMMENT '房主ID（总代理或小代理）',
    owner_name      VARCHAR(50)                                  COMMENT '房主名称',
    owner_role      INT                                          COMMENT '房主角色 2=小代理 3=总代理',
    room_id         BIGINT                                       COMMENT '房间ID',
    room_no         VARCHAR(10)                                  COMMENT '房间号（账单展示核心字段）',
    room_turnover   BIGINT       DEFAULT 0                       COMMENT '总流水',
    total_rake      BIGINT       DEFAULT 0                       COMMENT '房间总抽水',
    deducted_amount BIGINT       DEFAULT 0                       COMMENT '扣除金额（总流水3%）',
    rebate_amount   BIGINT       DEFAULT 0                       COMMENT '系统返还信用分（总流水1%）',
    net_cost        BIGINT       DEFAULT 0                       COMMENT '房主净成本=扣-返',
    before_credit   BIGINT                                       COMMENT '变动前余额',
    after_credit    BIGINT                                       COMMENT '变动后余额',
    status          INT          DEFAULT 0                       COMMENT '0=成功 1=失败(信用分不足)',
    fail_reason     VARCHAR(255)                                 COMMENT '失败原因',
    create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    KEY idx_rrl_owner (owner_id),
    KEY idx_rrl_room  (room_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='返佣账单表';

-- =====================================================================
-- 9. 平台公告表（原型 04/11 大厅公告滚动条 / 原型 20 系统消息来源）
-- =====================================================================
CREATE TABLE IF NOT EXISTS platform_announcement (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    title          VARCHAR(200)                                  COMMENT '标题',
    content        LONGTEXT                                      COMMENT '正文',
    top_flag       TINYINT(1)   DEFAULT 0                        COMMENT '是否置顶',
    publisher_id   BIGINT                                        COMMENT '发布人ID',
    publisher_name VARCHAR(50)                                   COMMENT '发布人名称',
    status         INT          DEFAULT 1                        COMMENT '1-发布 0-下架',
    create_time    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    deleted        INT          DEFAULT 0,
    KEY idx_pa_status (status, top_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台公告表';

-- =====================================================================
-- 10. 平台投诉表（原型 21-客服投诉页）
-- =====================================================================
CREATE TABLE IF NOT EXISTS platform_complaint (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT                                        COMMENT '提交人ID',
    username       VARCHAR(50)                                   COMMENT '提交人账号',
    complaint_type INT          DEFAULT 5                        COMMENT '投诉类型',
    title          VARCHAR(200)                                  COMMENT '标题',
    content        LONGTEXT                                      COMMENT '投诉内容',
    images         VARCHAR(500)                                  COMMENT '图片（逗号分隔 URL）',
    status         INT          DEFAULT 0                        COMMENT '0-待处理 1-处理中 2-已处理',
    handler_id     BIGINT                                        COMMENT '处理人ID',
    handler_name   VARCHAR(50)                                   COMMENT '处理人名称',
    reply          LONGTEXT                                      COMMENT '处理回复',
    handle_time    DATETIME                                      COMMENT '处理时间',
    create_time    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    KEY idx_pc_user (user_id),
    KEY idx_pc_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台投诉表';

-- =====================================================================
-- 11. 用户消息通知表（原型 20-消息通知页）
-- msg_type: 1-系统 2-佣金 3-投诉 4-充值 5-活动
-- =====================================================================
CREATE TABLE IF NOT EXISTS user_message (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT        NOT NULL                            COMMENT '接收者用户ID',
    msg_type    INT           NOT NULL DEFAULT 1                  COMMENT '1-系统 2-佣金 3-投诉 4-充值 5-活动',
    title       VARCHAR(200)                                      COMMENT '标题',
    content     VARCHAR(1000)                                     COMMENT '内容摘要',
    biz_id      BIGINT                                            COMMENT '关联业务ID（投诉单/提现单等）',
    read_flag   TINYINT(1)    DEFAULT 0                           COMMENT '0-未读 1-已读',
    read_time   DATETIME                                          COMMENT '已读时间',
    create_time DATETIME      DEFAULT CURRENT_TIMESTAMP,
    deleted     INT           DEFAULT 0,
    KEY idx_um_user (user_id, msg_type),
    KEY idx_um_unread (user_id, read_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户消息通知表';

-- =====================================================================
-- 12. 充值订单表（原型 08-充值页）
-- status: 0-待支付 1-已支付 2-已取消 3-已失败
-- =====================================================================
CREATE TABLE IF NOT EXISTS recharge_order (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no       VARCHAR(40)   NOT NULL UNIQUE                  COMMENT '订单号',
    user_id        BIGINT        NOT NULL                         COMMENT '用户ID',
    username       VARCHAR(50)                                    COMMENT '账号',
    amount_yuan    DECIMAL(10,2) NOT NULL                         COMMENT '充值金额（元）',
    gift_credits   BIGINT        DEFAULT 0                        COMMENT '赠送游戏币',
    actual_credits BIGINT        NOT NULL                         COMMENT '实际到账游戏币',
    pay_method     VARCHAR(20)                                    COMMENT 'ALIPAY/WECHAT/BANK',
    status         INT           DEFAULT 0                        COMMENT '0-待支付 1-已支付 2-已取消 3-已失败',
    pay_time       DATETIME                                       COMMENT '支付时间',
    create_time    DATETIME      DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME      DEFAULT CURRENT_TIMESTAMP,
    deleted        INT           DEFAULT 0,
    KEY idx_ro_user (user_id),
    KEY idx_ro_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='充值订单表';

-- =====================================================================
-- 13. 提现订单表（原型 17-提现页）
-- status: 0-待处理 1-已到账 2-已驳回
-- =====================================================================
CREATE TABLE IF NOT EXISTS withdraw_order (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no        VARCHAR(40)   NOT NULL UNIQUE                 COMMENT '订单号',
    user_id         BIGINT        NOT NULL                        COMMENT '用户ID',
    username        VARCHAR(50)                                   COMMENT '账号',
    amount_yuan     DECIMAL(10,2) NOT NULL                        COMMENT '提现金额（元）',
    deduct_credits  BIGINT        NOT NULL                        COMMENT '扣减游戏币',
    withdraw_method VARCHAR(20)                                   COMMENT 'ALIPAY/WECHAT/BANK',
    account         VARCHAR(120)                                  COMMENT '收款账号',
    status          INT           DEFAULT 0                       COMMENT '0-待处理 1-已到账 2-已驳回',
    fail_reason     VARCHAR(255)                                  COMMENT '驳回原因',
    audit_id        BIGINT                                        COMMENT '审核人ID',
    audit_name      VARCHAR(50)                                   COMMENT '审核人名称',
    audit_time      DATETIME                                      COMMENT '审核时间',
    create_time     DATETIME      DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME      DEFAULT CURRENT_TIMESTAMP,
    deleted         INT           DEFAULT 0,
    KEY idx_wo_user (user_id),
    KEY idx_wo_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提现订单表';

-- =====================================================================
-- 14. 用户设置表（原型 23-设置页 / 45-牌桌设置面板）
-- =====================================================================
CREATE TABLE IF NOT EXISTS user_setting (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id           BIGINT     NOT NULL UNIQUE                  COMMENT '用户ID',
    sound_enabled     TINYINT(1) DEFAULT 1                        COMMENT '音效',
    music_enabled     TINYINT(1) DEFAULT 1                        COMMENT '背景音乐',
    vibrate_enabled   TINYINT(1) DEFAULT 0                        COMMENT '震动',
    auto_play_enabled TINYINT(1) DEFAULT 0                        COMMENT '自动挂机',
    quick_bet_enabled TINYINT(1) DEFAULT 0                        COMMENT '显示快捷下注按钮',
    language          VARCHAR(20) DEFAULT 'zh-CN'                 COMMENT '语言',
    create_time       DATETIME   DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME   DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户设置表';

-- =====================================================================
-- 15. 常见问题表（原型 21-客服投诉页）
-- =====================================================================
CREATE TABLE IF NOT EXISTS faq_item (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    question    VARCHAR(300) NOT NULL                             COMMENT '问题',
    answer      LONGTEXT                                          COMMENT '答案',
    sort_no     INT          DEFAULT 0                            COMMENT '排序号',
    status      INT          DEFAULT 1                            COMMENT '1-启用 0-停用',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    deleted     INT          DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='常见问题表';

-- =====================================================================
-- 16. 在线玩家快照表（原型 04/11 大厅左侧在线玩家列表）
-- =====================================================================
CREATE TABLE IF NOT EXISTS online_player (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT     NOT NULL UNIQUE                   COMMENT '用户ID',
    nickname         VARCHAR(50)                                  COMMENT '昵称',
    avatar           TEXT                                         COMMENT '头像 URL',
    credits          BIGINT     DEFAULT 0                         COMMENT '游戏币',
    game_type        VARCHAR(30)                                  COMMENT '所在游戏/房间玩法',
    room_no          VARCHAR(10)                                  COMMENT '所在房间号',
    last_active_time DATETIME   DEFAULT CURRENT_TIMESTAMP         COMMENT '最后活跃时间',
    create_time      DATETIME   DEFAULT CURRENT_TIMESTAMP,
    KEY idx_op_active (last_active_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='在线玩家快照表';
