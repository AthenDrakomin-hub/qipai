package com.poker.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.poker.platform.dto.*;
import com.poker.platform.entity.GameRoom;
import com.poker.platform.entity.RoomPlayer;
import com.poker.platform.mapper.GameRoomMapper;
import com.poker.platform.mapper.RoomPlayerMapper;
import com.poker.platform.security.UserContext;
import com.poker.platform.service.GamePlayService;
import com.poker.platform.service.RoomService;
import com.poker.platform.service.SettlementService;
import com.poker.platform.exception.BizException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 房间 / 对局 控制器
 */
@RestController
@RequestMapping("/room")
public class RoomController {

    @Autowired private RoomService roomService;
    @Autowired private GamePlayService gamePlayService;
    @Autowired private SettlementService settlementService;
    @Autowired private GameRoomMapper gameRoomMapper;
    @Autowired private RoomPlayerMapper roomPlayerMapper;

    /** 代理创建房间 */
    @PostMapping("/create")
    public R<GameRoom> create(@Validated @RequestBody CreateRoomDTO dto) {
        Long uid = UserContext.getUserId();
        return R.ok(roomService.createRoom(uid, dto));
    }

    /** 玩家加入房间（旧接口：精确按房号加入，兼容历史前端） */
    @PostMapping("/join")
    public R<Map<String, Object>> join(@Validated @RequestBody JoinRoomDTO dto) {
        if (dto.getRoomNo() == null || dto.getRoomNo().isEmpty()) {
            throw new BizException("房间号不能为空（无房间号请走 /room/smart-join 自动匹配）");
        }
        if (dto.getRoomNo().length() != 6) {
            throw new BizException("房间号必须为6位");
        }
        if (dto.getBuyinCredits() == null || dto.getBuyinCredits() <= 0) {
            throw new BizException("带入筹码不能为空且需大于0");
        }
        Long uid = UserContext.getUserId();
        RoomPlayer rp = roomService.joinRoom(uid, dto);
        GameRoom room = roomService.getRoomDetail(rp.getRoomId());
        List<RoomPlayer> players = roomService.listRoomPlayers(rp.getRoomId());
        Map<String, Object> res = new HashMap<>();
        res.put("room", room);
        res.put("me", rp);
        res.put("players", players);
        return R.ok(res);
    }

    /** 获取房间详情 + 玩家列表 */
    @GetMapping("/{roomId}")
    public R<Map<String, Object>> detail(@PathVariable Long roomId) {
        GameRoom room = roomService.getRoomDetail(roomId);
        List<RoomPlayer> players = roomService.listRoomPlayers(roomId);
        Map<String, Object> res = new HashMap<>();
        res.put("room", room);
        res.put("players", players);
        return R.ok(res);
    }

    /** 根据房间号查询 */
    @GetMapping("/no/{roomNo}")
    public R<GameRoom> byNo(@PathVariable String roomNo) {
        return R.ok(roomService.getRoomByNo(roomNo));
    }

    /** 我创建的房间列表（代理） */
    @GetMapping("/my-owned")
    public R<List<GameRoom>> myOwned() {
        Long uid = UserContext.getUserId();
        List<GameRoom> list = gameRoomMapper.selectList(new LambdaQueryWrapper<GameRoom>()
                .eq(GameRoom::getOwnerId, uid)
                .orderByDesc(GameRoom::getCreateTime));
        return R.ok(list);
    }

    /** 我所在的房间（玩家/代理旁观） */
    @GetMapping("/my-joined")
    public R<List<GameRoom>> myJoined() {
        Long uid = UserContext.getUserId();
        // 通过 RoomPlayer 查询玩家所在 roomId 集合（非观察者 + 观察者）
        List<RoomPlayer> rps = roomPlayerMapper.selectList(new LambdaQueryWrapper<RoomPlayer>()
                .eq(RoomPlayer::getUserId, uid));
        if (rps == null || rps.isEmpty()) return R.ok(java.util.Collections.emptyList());
        List<Long> ids = rps.stream().map(RoomPlayer::getRoomId).distinct().collect(java.util.stream.Collectors.toList());
        List<GameRoom> list = gameRoomMapper.selectBatchIds(ids);
        return R.ok(list == null ? java.util.Collections.emptyList() : list);
    }

    /** 游戏大厅：公开房间列表（所有已登录用户均可见；无密码+未关闭未结算） */
    @GetMapping("/lobby")
    public R<List<Map<String, Object>>> lobby(@RequestParam(required = false) String gameType,
                                              @RequestParam(required = false) String roomLevel) {
        return R.ok(roomService.lobbyList(gameType, roomLevel));
    }

    /**
     * 智能加入：
     *   - 玩家填写了 roomNo → 优先按房号直接加入（同时校验密码、带入筹码）
     *   - 没有填写 roomNo → 自动匹配按 gameType + roomLevel 的公开未满房间
     * 为避免"已在房间内+无房号"时冲突，smartJoin 自动跳过玩家已加入的房间。
     */
    @PostMapping("/smart-join")
    public R<Map<String, Object>> smartJoin(@Validated @RequestBody JoinRoomDTO dto) {
        Long uid = UserContext.getUserId();
        RoomPlayer rp = roomService.smartJoin(uid, dto);
        GameRoom room = roomService.getRoomDetail(rp.getRoomId());
        List<RoomPlayer> players = roomService.listRoomPlayers(rp.getRoomId());
        Map<String, Object> res = new HashMap<>();
        res.put("room", room);
        res.put("me", rp);
        res.put("players", players);
        res.put("joinMode",
                (dto.getRoomNo() != null && !dto.getRoomNo().isEmpty()) ? "roomNo" : "autoMatch");
        return R.ok(res);
    }

    /**
     * 公域匹配（需求文档第十章）：
     *   玩家不依赖代理邀请，系统自动匹配进公域房。
     *   结算规则：平台总抽 0.3%，各代理层级各 0.15% 依次分佣。
     */
    @PostMapping("/public-match")
    public R<Map<String, Object>> publicMatch(@Validated @RequestBody JoinRoomDTO dto) {
        Long uid = UserContext.getUserId();
        RoomPlayer rp = roomService.publicMatch(uid, dto);
        GameRoom room = roomService.getRoomDetail(rp.getRoomId());
        List<RoomPlayer> players = roomService.listRoomPlayers(rp.getRoomId());
        Map<String, Object> res = new HashMap<>();
        res.put("room", room);
        res.put("me", rp);
        res.put("players", players);
        res.put("matchMode", "publicMatch");
        return R.ok(res);
    }

    /** 关闭房间 */
    @PostMapping("/{roomId}/close")    public R<Void> close(@PathVariable Long roomId) {
        roomService.closeRoom(roomId, UserContext.getUserId());
        return R.ok();
    }

    /** 玩家离开房间（退还剩余游戏币，删除入座记录） */
    @PostMapping("/{roomId}/leave")
    public R<Void> leave(@PathVariable Long roomId) {
        roomService.leaveRoom(UserContext.getUserId(), roomId);
        return R.ok();
    }

    /**
     * 在线调整房间参数（最小化：maxPlayers / roomName）。
     * 仅允许：房主（代理/总代理）、超管。
     * 调整后的 maxPlayers 不能小于当前房间已入座玩家数（非观察者）。
     */
    public static class UpdateRoomDTO {
        public Integer maxPlayers;
        public String roomName;
    }

    @PatchMapping("/{roomId}")
    public R<GameRoom> updateRoom(@PathVariable Long roomId, @RequestBody UpdateRoomDTO dto) {
        Long opId = UserContext.getUserId();
        Integer opRole = UserContext.getRole();
        GameRoom room = gameRoomMapper.selectById(roomId);
        if (room == null) throw new BizException("房间不存在");
        boolean isOwner = room.getOwnerId() != null && room.getOwnerId().equals(opId);
        boolean isAdmin = opRole != null && opRole == 5; // SUPER_ADMIN
        if (!isOwner && !isAdmin) {
            throw new BizException("无权限修改该房间（仅房主或超管可调整）");
        }
        if (room.getStatus() != null && (room.getStatus() == 2 || room.getStatus() == 3)) {
            throw new BizException("房间已结算或已关闭，无法再调整人数");
        }

        // 计算当前已入座玩家数（非观察者）
        long seated = 0;
        try {
            Long s = roomPlayerMapper.selectCount(new LambdaQueryWrapper<RoomPlayer>()
                    .eq(RoomPlayer::getRoomId, roomId)
                    .and(w -> w.isNull(RoomPlayer::getIsObserver).or().eq(RoomPlayer::getIsObserver, false)));
            seated = s == null ? 0L : s;
        } catch (Exception ignored) {
            List<RoomPlayer> rps = roomPlayerMapper.selectList(new LambdaQueryWrapper<RoomPlayer>()
                    .eq(RoomPlayer::getRoomId, roomId));
            if (rps != null) seated = rps.stream().filter(p -> p.getIsObserver() == null || !p.getIsObserver()).count();
        }

        boolean changed = false;
        if (dto != null && dto.maxPlayers != null) {
            int mp = dto.maxPlayers;
            if (mp < 2 || mp > 20) {
                throw new BizException("房间人数必须在 2 - 20 之间");
            }
            if (mp < seated) {
                throw new BizException(String.format(
                        "已入座玩家 %d 人，新的 maxPlayers(%d) 不能小于在房人数，请先关闭房间后重建或移除玩家",
                        seated, mp));
            }
            room.setMaxPlayers(mp);
            changed = true;
        }
        if (dto != null && dto.roomName != null) {
            String nm = dto.roomName.trim();
            if (nm.length() < 2 || nm.length() > 32) {
                throw new BizException("房间名长度需在 2-32 位之间");
            }
            room.setRoomName(nm);
            changed = true;
        }
        if (changed) gameRoomMapper.updateById(room);
        return R.ok(room);
    }

    // ============ 对局 ============

    /** 进行一局（自动发牌+结算，可传 bets 参数自定义下注） */
    @PostMapping("/{roomId}/play-round")
    public R<Map<String, Object>> playRound(@PathVariable Long roomId,
                                            @RequestBody(required = false) Map<String, Object> body) {
        Map<Long, Long> bets = null;
        Integer roundNo = null;
        if (body != null) {
            Object b = body.get("bets");
            if (b instanceof Map) {
                bets = new HashMap<>();
                for (Map.Entry<?, ?> e : ((Map<?,?>) b).entrySet()) {
                    bets.put(Long.valueOf(String.valueOf(e.getKey())),
                            Long.valueOf(String.valueOf(e.getValue())));
                }
            }
            Object r = body.get("roundNo");
            if (r != null) roundNo = Integer.valueOf(String.valueOf(r));
        }
        return R.ok(gamePlayService.playOneRound(roomId, bets, roundNo));
    }

    /** 切换���动挂机（通比类游戏：开启后每局自动下注参与，无需手动操作） */
    @PostMapping("/{roomId}/auto-play")
    public R<RoomPlayer> toggleAutoPlay(@PathVariable Long roomId) {
        Long uid = UserContext.getUserId();
        RoomPlayer rp = roomPlayerMapper.selectOne(new LambdaQueryWrapper<RoomPlayer>()
                .eq(RoomPlayer::getRoomId, roomId)
                .eq(RoomPlayer::getUserId, uid)
                .and(w -> w.isNull(RoomPlayer::getIsObserver).or().eq(RoomPlayer::getIsObserver, false)));
        if (rp == null) throw new BizException("您不在此房间座位上，无法切换自动挂机");
        boolean now = rp.getIsAutoPlay() != null && rp.getIsAutoPlay();
        rp.setIsAutoPlay(!now);
        roomPlayerMapper.updateById(rp);
        return R.ok(rp);
    }

    /**
     * 金花专用：看牌（闷牌 → 看牌）。
     * 看牌后本局及后续下注按 lookBet（=闷注的2倍）执行；一次性，不可逆回闷牌。
     */
    @PostMapping("/{roomId}/look")
    public R<RoomPlayer> lookCards(@PathVariable Long roomId) {
        Long uid = UserContext.getUserId();
        GameRoom room = gameRoomMapper.selectById(roomId);
        if (room == null) throw new BizException("房间不存在");
        if (!"JINHUA".equals(room.getGameType())) {
            throw new BizException("仅金花玩法支持看牌/闷牌");
        }
        RoomPlayer rp = roomPlayerMapper.selectOne(new LambdaQueryWrapper<RoomPlayer>()
                .eq(RoomPlayer::getRoomId, roomId)
                .eq(RoomPlayer::getUserId, uid)
                .and(w -> w.isNull(RoomPlayer::getIsObserver).or().eq(RoomPlayer::getIsObserver, false)));
        if (rp == null) throw new BizException("您不在此房间座位上，无法看牌");
        rp.setIsLooked(true);
        roomPlayerMapper.updateById(rp);
        return R.ok(rp);
    }

    /** 手动触发总局结算（达到25局时 play-round 已自动触发，这里提供强制结算接口） */
    @PostMapping("/{roomId}/settle")
    public R<Map<String, Object>> settle(@PathVariable Long roomId) {
        return R.ok(settlementService.settleRoom(roomId));
    }
}
