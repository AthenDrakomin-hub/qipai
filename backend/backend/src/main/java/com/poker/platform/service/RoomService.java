package com.poker.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.poker.platform.config.PlatformConfig;
import com.poker.platform.dto.CreateRoomDTO;
import com.poker.platform.dto.JoinRoomDTO;
import com.poker.platform.entity.GameRoom;
import com.poker.platform.entity.RoomPlayer;
import com.poker.platform.entity.User;
import com.poker.platform.enums.GameType;
import com.poker.platform.enums.RoomLevel;
import com.poker.platform.enums.RoomStatus;
import com.poker.platform.enums.UserRole;
import com.poker.platform.exception.BizException;
import com.poker.platform.game.GameBetConfig;
import com.poker.platform.mapper.GameRoomMapper;
import com.poker.platform.mapper.RoomPlayerMapper;
import com.poker.platform.mapper.UserMapper;
import com.poker.platform.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

/**
 * 房间服务
 */
@Service
public class RoomService {

    private static final Logger log = LoggerFactory.getLogger(RoomService.class);

    @Resource
    private GameRoomMapper roomMapper;

    @Resource
    private RoomPlayerMapper roomPlayerMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private PlatformConfig platformConfig;
    
    @Resource
    private CreditService creditService;

    /**
     * 创建房间（二级代理 / 总代理均有权限，总代理也可以直接创建房间）
     * —— 风控（按需求文档）：
     *    1) 存在水费扣费失败记录 → 禁止新建房间（需先补扣欠费 / 联系客服处理）
     *    2) 游戏币低于房间等级门槛 → 禁止开对应等级房间
     *       （初级需 ≥100，高级需 ≥1000，顶级需 ≥3000）
     */
    @Transactional(rollbackFor = Exception.class)
    public GameRoom createRoom(Long agentId, CreateRoomDTO dto) {
        User agent = userMapper.selectById(agentId);
        if (agent == null) throw new BizException("用户不存在");
        if (!UserRole.isAgent(agent.getRole())) {
            throw new BizException("仅代理（一级/二级/总代）有权限创建房间");
        }
        // 风控 1：水费欠费禁开房
        if (agent.getHasFeeFailure() != null && agent.getHasFeeFailure()) {
            throw new BizException("您存在水费扣费失败记录，禁止新建房间，请先充值补足欠费或联系客服处理");
        }

        GameType gameType = GameType.fromCode(dto.getGameType());
        if (gameType == null) throw new BizException("不支持的游戏类型");
        RoomLevel level = RoomLevel.fromCode(dto.getRoomLevel());
        if (level == null) throw new BizException("不支持的房间等级");

        // 风控 2：游戏币门槛校验（<100 一律禁开；≥1000 可开高级；≥3000 可开顶级）
        long credits = agent.getCredits() == null ? 0L : agent.getCredits();
        if (credits < 100) {
            throw new BizException("游戏币不足 100，禁止开房，请先充值");
        }
        if (credits < level.getCreditThreshold()) {
            throw new BizException(String.format(
                    "当前游戏币 %d 未达到【%s】房间门槛 %d，请充值后开房",
                    credits, level.getDesc(), level.getCreditThreshold()));
        }

        // 生成6位唯一房间号
        String roomNo;
        int retry = 0;
        do {
            roomNo = JwtUtil.generateRoomNo();
            Long c = roomMapper.selectCount(new LambdaQueryWrapper<GameRoom>().eq(GameRoom::getRoomNo, roomNo));
            if (c == null || c == 0) break;
            retry++;
        } while (retry < 50);

        GameRoom room = new GameRoom();
        room.setRoomNo(roomNo);
        room.setRoomPassword(dto.getRoomPassword());
        room.setGameType(gameType.getCode());
        room.setRoomLevel(level.getCode());
        room.setRoomName((dto.getRoomName() == null || dto.getRoomName().isEmpty())
                ? gameType.getDesc() + "-" + level.getDesc() : dto.getRoomName());
        room.setOwnerId(agentId);
        room.setOwnerInviteCode(agent.getInviteCode());
        room.setMaxPlayers(dto.getMaxPlayers() == null ? 8 : dto.getMaxPlayers());
        room.setMinBuyin(level.getMinBuyin());
        room.setMaxBuyin(level.getMaxBuyin());
        room.setStatus(RoomStatus.WAITING.getCode());
        room.setCurrentRound(0);
        room.setTotalRounds(platformConfig.getRoundsPerRoom());
        room.setTotalTurnover(0L);
        room.setTotalRake(0L);
        room.setTotalWaterFee(0L);
        // 按游戏类型设置固定下注额：通比类校验房主设定区间，其他类记录底注供参考
        GameBetConfig.BetParam betParam = GameBetConfig.get(gameType, level);
        long fixedBet;
        if (gameType == GameType.TONGBI_NIUNIU || gameType == GameType.TONGBI_SANGONG) {
            long want = dto.getFixedBetAmount() == null ? betParam.fixedMin : dto.getFixedBetAmount();
            fixedBet = GameBetConfig.clampFixedBet(gameType, level, want);
        } else {
            fixedBet = betParam.baseBet;
        }
        room.setFixedBetAmount(fixedBet);
        roomMapper.insert(room);

        // 房主自动进入观众位（代理仅旁观，不参与游戏）
        RoomPlayer observer = new RoomPlayer();
        observer.setRoomId(room.getId());
        observer.setRoomNo(roomNo);
        observer.setUserId(agentId);
        observer.setUsername(agent.getUsername());
        observer.setNickname(agent.getNickname());
        observer.setSeatNo(-1);
        observer.setIsObserver(true);
        observer.setBuyinCredits(0L);
        observer.setCurrentCredits(0L);
        observer.setTotalProfit(0L);
        roomPlayerMapper.insert(observer);

        log.info("创建房间成功: agent={} roomNo={} game={} level={}", agent.getUsername(), roomNo, gameType, level);
        return room;
    }

    /**
     * 玩家加入房间
     */
    @Transactional(rollbackFor = Exception.class)
    public RoomPlayer joinRoom(Long playerId, JoinRoomDTO dto) {
        User player = userMapper.selectById(playerId);
        if (player == null) throw new BizException("用户不存在");
        if (UserRole.PLAYER.getCode() != player.getRole()) {
            throw new BizException("仅普通玩家可加入房间参与游戏");
        }

        GameRoom room = roomMapper.selectOne(new LambdaQueryWrapper<GameRoom>()
                .eq(GameRoom::getRoomNo, dto.getRoomNo()));
        if (room == null) throw new BizException("房间不存在");
        if (RoomStatus.CLOSED.getCode().equals(room.getStatus())) {
            throw new BizException("房间已关闭");
        }
        if (RoomStatus.SETTLED.getCode().equals(room.getStatus())) {
            throw new BizException("房间已完成结算");
        }
        // 房间密码
        if (room.getRoomPassword() != null && !room.getRoomPassword().isEmpty()) {
            if (!room.getRoomPassword().equals(dto.getRoomPassword())) {
                throw new BizException("房间密码错误");
            }
        }
        // 带入筹码区间校验：普通玩家统一 200 - 2000（需求文档第三章）
        long minBuyin = platformConfig.getPlayerMinBuyin();
        long maxBuyin = platformConfig.getPlayerMaxBuyin();
        long buyin = (dto.getBuyinCredits() == null || dto.getBuyinCredits() <= 0) ? minBuyin : dto.getBuyinCredits();
        if (buyin < minBuyin) {
            throw new BizException("带入筹码不能低于 " + minBuyin);
        }
        if (buyin > maxBuyin) {
            throw new BizException("带入筹码不能高于 " + maxBuyin);
        }
        // 玩家余额校验
        long playerCredits = player.getCredits() == null ? 0L : player.getCredits();
        if (playerCredits < buyin) {
            throw new BizException("您的积分不足，请联系您的上级代理充值（当前：" + playerCredits + "）");
        }

        // 座位检查
        List<RoomPlayer> players = roomPlayerMapper.selectList(new LambdaQueryWrapper<RoomPlayer>()
                .eq(RoomPlayer::getRoomId, room.getId())
                .eq(RoomPlayer::getIsObserver, false));
        if (players.size() >= room.getMaxPlayers()) {
            throw new BizException("房间人数已满");
        }
        // 检查是否已在房间
        long exists = players.stream().filter(p -> p.getUserId().equals(playerId)).count();
        if (exists > 0) {
            throw new BizException("您已在房间内");
        }

        // 占用一个空座位
        int seat = 0;
        for (int i = 0; i < room.getMaxPlayers(); i++) {
            final int si = i;
            boolean used = players.stream().anyMatch(p -> p.getSeatNo() != null && p.getSeatNo() == si);
            if (!used) { seat = i; break; }
        }

        // 扣除玩家积分 -> 转移到房间
        creditService.changeCredits(playerId, -buyin, 2, room.getId(), room.getRoomNo(), null,
                null, null, "加入房间[" + room.getRoomNo() + "]锁定带入筹码", false);

        RoomPlayer rp = new RoomPlayer();
        rp.setRoomId(room.getId());
        rp.setRoomNo(room.getRoomNo());
        rp.setUserId(playerId);
        rp.setUsername(player.getUsername());
        rp.setNickname(player.getNickname());
        rp.setSeatNo(seat);
        rp.setIsObserver(false);
        rp.setBuyinCredits(buyin);
        rp.setCurrentCredits(buyin);
        rp.setTotalProfit(0L);
        roomPlayerMapper.insert(rp);

        log.info("加入房间: player={} room={} buyin={}", player.getUsername(), room.getRoomNo(), buyin);
        return rp;
    }

    /**
     * 获取房间详情 + 玩家列表
     */
    public GameRoom getRoomDetail(Long roomId) {
        return roomMapper.selectById(roomId);
    }

    public GameRoom getRoomByNo(String roomNo) {
        return roomMapper.selectOne(new LambdaQueryWrapper<GameRoom>().eq(GameRoom::getRoomNo, roomNo));
    }

    public List<RoomPlayer> listRoomPlayers(Long roomId) {
        return roomPlayerMapper.selectList(new LambdaQueryWrapper<RoomPlayer>()
                .eq(RoomPlayer::getRoomId, roomId)
                .orderByAsc(RoomPlayer::getSeatNo));
    }

    /**
     * 关闭房间（仅房主或管理员）
     */
    @Transactional(rollbackFor = Exception.class)
    public void closeRoom(Long roomId, Long operatorId) {
        GameRoom room = roomMapper.selectById(roomId);
        if (room == null) throw new BizException("房间不存在");
        if (!room.getOwnerId().equals(operatorId)) {
            User op = userMapper.selectById(operatorId);
            if (op == null || (op.getRole() != 4 && op.getRole() != 5)) {
                throw new BizException("无权限关闭该房间");
            }
        }
        room.setStatus(RoomStatus.CLOSED.getCode());
        roomMapper.updateById(room);
    }

    // ========== 游戏大厅 / 公开房间 / 自动匹配 ==========

    /**
     * 游戏大厅房间列表（所有玩家/游客可见）
     * —— 仅返回无密码 + 未关闭/未结算 的房间；可以按游戏类型/房间等级过滤
     * —— 附带每个房间当前玩家数（非观察者）
     */
    public List<Map<String, Object>> lobbyList(String gameType, String roomLevel) {
        autoCloseStaleEmptyRooms();
        LambdaQueryWrapper<GameRoom> q = new LambdaQueryWrapper<GameRoom>()
                .in(GameRoom::getStatus, RoomStatus.WAITING.getCode(), RoomStatus.PLAYING.getCode())
                .and(w -> w.isNull(GameRoom::getRoomPassword).or().eq(GameRoom::getRoomPassword, ""));
        if (gameType != null && !gameType.isEmpty()) q.eq(GameRoom::getGameType, gameType);
        if (roomLevel != null && !roomLevel.isEmpty()) q.eq(GameRoom::getRoomLevel, roomLevel);
        q.orderByDesc(GameRoom::getCreateTime);
        List<GameRoom> list = roomMapper.selectList(q);
        List<Map<String, Object>> result = new java.util.ArrayList<>(list.size());
        for (GameRoom room : list) {
            long count = roomPlayerMapper.selectCount(new LambdaQueryWrapper<RoomPlayer>()
                    .eq(RoomPlayer::getRoomId, room.getId())
                    .eq(RoomPlayer::getIsObserver, false));
            // 房主昵称（若房主用户找不到，则落空）
            String ownerNickname = null;
            if (room.getOwnerId() != null) {
                try {
                    User owner = userMapper.selectById(room.getOwnerId());
                    if (owner != null) ownerNickname = owner.getNickname() == null ? owner.getUsername() : owner.getNickname();
                } catch (Exception ignored) {}
            }
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", room.getId());
            m.put("roomNo", room.getRoomNo());
            m.put("roomName", room.getRoomName());
            m.put("gameType", room.getGameType());
            m.put("gameTypeDesc", GameType.fromCode(room.getGameType()) == null ? ""
                    : GameType.fromCode(room.getGameType()).getDesc());
            m.put("roomLevel", room.getRoomLevel());
            m.put("roomLevelDesc", RoomLevel.fromCode(room.getRoomLevel()) == null ? ""
                    : RoomLevel.fromCode(room.getRoomLevel()).getDesc());
            m.put("status", room.getStatus());
            m.put("ownerId", room.getOwnerId());
            m.put("ownerNickname", ownerNickname);
            m.put("ownerInviteCode", room.getOwnerInviteCode());
            m.put("minBuyin", room.getMinBuyin());
            m.put("maxBuyin", room.getMaxBuyin());
            m.put("maxPlayers", room.getMaxPlayers());
            m.put("currentPlayers", (int) count);
            m.put("hasPassword", false);
            m.put("createTime", room.getCreateTime());
            result.add(m);
        }
        return result;
    }

    /**
     * 自动关闭长时间空置的房间（等待中/对局中 且 0 名玩家、创建超过 2 小时）。
     * 避免历史遗留空房长期挂在大厅刷屏；每次大厅查询时惰性触发。
     */
    private void autoCloseStaleEmptyRooms() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(2);
        List<GameRoom> stale = roomMapper.selectList(new LambdaQueryWrapper<GameRoom>()
                .in(GameRoom::getStatus, RoomStatus.WAITING.getCode(), RoomStatus.PLAYING.getCode())
                .lt(GameRoom::getCreateTime, threshold));
        if (stale.isEmpty()) return;
        for (GameRoom room : stale) {
            long players = roomPlayerMapper.selectCount(new LambdaQueryWrapper<RoomPlayer>()
                    .eq(RoomPlayer::getRoomId, room.getId())
                    .eq(RoomPlayer::getIsObserver, false));
            if (players == 0) {
                room.setStatus(RoomStatus.CLOSED.getCode());
                roomMapper.updateById(room);
            }
        }
    }

    /**
     * 按房间号优先加入（玩家输入房号，直接进入）。
     * —— 若填写了房间号且校验通过，直接按房号 join（密码也要）
     * —— 否则 fallback 到自动匹配逻辑：按 gameType + roomLevel 匹配最近可用的 WAITING 房间，取未满员的第一个；找不到抛错，返回提示"请稍后再试或换等级"。
     *
     * 返回 Join 结果（同 joinRoom），如果是自动匹配匹配失败，也通过 throw BizException("当前大厅暂无匹配的空房，请稍后再试或联系代理创建") 提示。
     */
    @Transactional(rollbackFor = Exception.class)
    public RoomPlayer smartJoin(Long playerId, JoinRoomDTO dto) {
        // 1) 输入房号优先：只要传了非空 roomNo，就走精确加入
        if (dto.getRoomNo() != null && !dto.getRoomNo().trim().isEmpty()) {
            return joinRoom(playerId, dto);
        }

        // 2) 自动匹配（需要 gameType + roomLevel）
        GameType gameType = GameType.fromCode(dto.getGameType());
        RoomLevel level = RoomLevel.fromCode(dto.getRoomLevel());
        if (gameType == null) throw new BizException("自动匹配：请选择游戏类型");
        if (level == null) throw new BizException("自动匹配：请选择房间等级");

        // 普通玩家带入统一 200 - 2000（与 joinRoom 保持一致）
        long pMin = platformConfig.getPlayerMinBuyin();
        long pMax = platformConfig.getPlayerMaxBuyin();
        long buyin = dto.getBuyinCredits() == null ? pMin : dto.getBuyinCredits();
        if (buyin < pMin) buyin = pMin;
        if (buyin > pMax) buyin = pMax;

        // 查公开无密码的 WAITING 或 PLAYING 未满房间（同类型+同等级）
        List<GameRoom> rooms = roomMapper.selectList(new LambdaQueryWrapper<GameRoom>()
                .eq(GameRoom::getGameType, gameType.getCode())
                .eq(GameRoom::getRoomLevel, level.getCode())
                .in(GameRoom::getStatus, RoomStatus.WAITING.getCode(), RoomStatus.PLAYING.getCode())
                .and(w -> w.isNull(GameRoom::getRoomPassword).or().eq(GameRoom::getRoomPassword, ""))
                .orderByAsc(GameRoom::getCreateTime));
        // 找"未满员+玩家未加入"的第一个房间
        for (GameRoom room : rooms) {
            List<RoomPlayer> players = roomPlayerMapper.selectList(new LambdaQueryWrapper<RoomPlayer>()
                    .eq(RoomPlayer::getRoomId, room.getId())
                    .eq(RoomPlayer::getIsObserver, false));
            long me = players.stream().filter(p -> p.getUserId().equals(playerId)).count();
            if (me > 0) continue; // 玩家已在房里 → 跳过（再进会抛冲突）
            if (players.size() >= room.getMaxPlayers()) continue;
            // 命中，执行 join
            JoinRoomDTO joinDto = new JoinRoomDTO();
            joinDto.setRoomNo(room.getRoomNo());
            joinDto.setRoomPassword(null);
            joinDto.setBuyinCredits(buyin);
            return joinRoom(playerId, joinDto);
        }
        throw new BizException("当前大厅暂无匹配的空房，请稍后再试或联系代理创建房间");
    }

    /**
     * 公域匹配（需求文档第十章）：
     *   —— 玩家不依赖代理邀请，由系统自动匹配进"公域房"（无房主、无密码）。
     *   —— 公域房按 gameType + roomLevel 复用：已存在 WAITING/PLAYING 且未满员则直接加入；
     *      否则新建一间公域房（isPublicMatch=true，按首个匹配玩家的上级代理记为 matchAgentId）。
     *   —— 结算走公域规则（平台总抽0.3% + 各代理0.15%依次分佣）。
     */
    @Transactional(rollbackFor = Exception.class)
    public RoomPlayer publicMatch(Long playerId, JoinRoomDTO dto) {
        User player = userMapper.selectById(playerId);
        if (player == null) throw new BizException("用户不存在");
        if (UserRole.PLAYER.getCode() != player.getRole()) {
            throw new BizException("仅普通玩家可参与公域匹配");
        }
        GameType gameType = GameType.fromCode(dto.getGameType());
        RoomLevel level = RoomLevel.fromCode(dto.getRoomLevel());
        if (gameType == null) throw new BizException("公域匹配：请选择游戏类型");
        if (level == null) throw new BizException("公域匹配：请选择房间等级");

        long pMin = platformConfig.getPlayerMinBuyin();
        long pMax = platformConfig.getPlayerMaxBuyin();
        long buyin = dto.getBuyinCredits() == null ? pMin : dto.getBuyinCredits();
        if (buyin < pMin) buyin = pMin;
        if (buyin > pMax) buyin = pMax;

        // 找已存在且未满员的公域房
        List<GameRoom> rooms = roomMapper.selectList(new LambdaQueryWrapper<GameRoom>()
                .eq(GameRoom::getIsPublicMatch, true)
                .eq(GameRoom::getGameType, gameType.getCode())
                .eq(GameRoom::getRoomLevel, level.getCode())
                .in(GameRoom::getStatus, RoomStatus.WAITING.getCode(), RoomStatus.PLAYING.getCode())
                .orderByAsc(GameRoom::getCreateTime));
        for (GameRoom room : rooms) {
            List<RoomPlayer> players = roomPlayerMapper.selectList(new LambdaQueryWrapper<RoomPlayer>()
                    .eq(RoomPlayer::getRoomId, room.getId())
                    .eq(RoomPlayer::getIsObserver, false));
            long me = players.stream().filter(p -> p.getUserId().equals(playerId)).count();
            if (me > 0) continue;
            if (players.size() >= room.getMaxPlayers()) continue;
            JoinRoomDTO jd = new JoinRoomDTO();
            jd.setRoomNo(room.getRoomNo());
            jd.setBuyinCredits(buyin);
            return joinRoom(playerId, jd);
        }

        // 新建公域房
        GameRoom room = createPublicMatchRoom(player, gameType, level);
        JoinRoomDTO jd = new JoinRoomDTO();
        jd.setRoomNo(room.getRoomNo());
        jd.setBuyinCredits(buyin);
        return joinRoom(playerId, jd);
    }

    /** 创建公域匹配房（系统房：无房主、无密码、ownerId 记为首个玩家，便于审计；matchAgentId 记其上级代理） */
    private GameRoom createPublicMatchRoom(User firstPlayer, GameType gameType, RoomLevel level) {
        String roomNo;
        int retry = 0;
        do {
            roomNo = JwtUtil.generateRoomNo();
            Long c = roomMapper.selectCount(new LambdaQueryWrapper<GameRoom>().eq(GameRoom::getRoomNo, roomNo));
            if (c == null || c == 0) break;
            retry++;
        } while (retry < 50);

        GameRoom room = new GameRoom();
        room.setRoomNo(roomNo);
        room.setRoomPassword(null);
        room.setGameType(gameType.getCode());
        room.setRoomLevel(level.getCode());
        room.setRoomName("公域-" + gameType.getDesc() + "-" + level.getDesc());
        room.setOwnerId(firstPlayer.getId());
        room.setOwnerInviteCode(null);
        room.setMaxPlayers(8);
        room.setMinBuyin(platformConfig.getPlayerMinBuyin());
        room.setMaxBuyin(platformConfig.getPlayerMaxBuyin());
        room.setStatus(RoomStatus.WAITING.getCode());
        room.setCurrentRound(0);
        room.setTotalRounds(platformConfig.getRoundsPerRoom());
        room.setTotalTurnover(0L);
        room.setTotalRake(0L);
        room.setTotalWaterFee(0L);
        room.setIsPublicMatch(true);
        // 绑定首个玩家的上级代理链路起点（若上级为代理）
        User parent = firstPlayer.getParentId() == null ? null : userMapper.selectById(firstPlayer.getParentId());
        if (parent != null && UserRole.isAgent(parent.getRole())) {
            room.setMatchAgentId(parent.getId());
        }
        GameBetConfig.BetParam bp = GameBetConfig.get(gameType, level);
        long fixedBet = (gameType == GameType.TONGBI_NIUNIU || gameType == GameType.TONGBI_SANGONG)
                ? bp.fixedMin : bp.baseBet;
        room.setFixedBetAmount(fixedBet);
        roomMapper.insert(room);
        log.info("创建公域匹配房 roomNo={} game={} level={} matchAgent={}", roomNo, gameType, level, room.getMatchAgentId());
        return room;
    }

    /**
     * 玩家离开房间
     * - 退还当前剩余游戏币到玩家账户
     * 删除 RoomPlayer 记录
     */
    @Transactional(rollbackFor = Exception.class)
    public void leaveRoom(Long playerId, Long roomId) {
        RoomPlayer rp = roomPlayerMapper.selectOne(new LambdaQueryWrapper<RoomPlayer>()
                .eq(RoomPlayer::getRoomId, roomId)
                .eq(RoomPlayer::getUserId, playerId));
        if (rp == null) {
            // 不在房间里，直接返回（幂等）
            return;
        }
        // 退还剩余游戏币
        long refund = rp.getCurrentCredits() != null ? rp.getCurrentCredits() : 0L;
        if (refund > 0) {
            GameRoom room = roomMapper.selectById(roomId);
            creditService.changeCredits(playerId, refund, 2, roomId,
                    room != null ? room.getRoomNo() : null, null, null, null,
                    "离开房间[" + (room != null ? room.getRoomNo() : "") + "]退还带入筹码", false);
        }
        roomPlayerMapper.deleteById(rp.getId());
        log.info("离开房间: player={} roomId={} 退还={}", playerId, roomId, refund);
    }
}
