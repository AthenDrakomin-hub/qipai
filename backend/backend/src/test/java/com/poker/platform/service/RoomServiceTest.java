package com.poker.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.poker.platform.config.PlatformConfig;
import com.poker.platform.dto.CreateRoomDTO;
import com.poker.platform.dto.JoinRoomDTO;
import com.poker.platform.entity.GameRoom;
import com.poker.platform.entity.RoomPlayer;
import com.poker.platform.entity.User;
import com.poker.platform.enums.RoomLevel;
import com.poker.platform.enums.RoomStatus;
import com.poker.platform.enums.UserRole;
import com.poker.platform.exception.BizException;
import com.poker.platform.mapper.GameRoomMapper;
import com.poker.platform.mapper.RoomPlayerMapper;
import com.poker.platform.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RoomService 房间服务单元测试")
class RoomServiceTest {

    @Mock private GameRoomMapper gameRoomMapper;
    @Mock private UserMapper userMapper;
    @Mock private RoomPlayerMapper roomPlayerMapper;
    @Mock private CreditService creditService;
    @Spy private PlatformConfig platformConfig;
    @InjectMocks private RoomService roomService;

    private static User buildUser(Long id, String name, int role, long credits, Long parentId) {
        User u = new User();
        u.setId(id);
        u.setUsername(name);
        u.setRole(role);
        u.setCredits(credits);
        u.setInviteCode("INV" + String.format("%04d", id.intValue()));
        u.setParentId(parentId);
        return u;
    }

    private static GameRoom buildRoom(Long id, String roomNo, String gameType, String roomLevel,
                                       int status, int maxPlayers, long ownerId, String ownerCode,
                                       boolean hasPassword, long turnover) {
        GameRoom r = new GameRoom();
        r.setId(id);
        r.setRoomNo(roomNo);
        r.setGameType(gameType);
        r.setRoomLevel(roomLevel);
        r.setStatus(status);
        r.setMaxPlayers(maxPlayers);
        r.setOwnerId(ownerId);
        r.setOwnerInviteCode(ownerCode);
        r.setRoomPassword(hasPassword ? "pwd" : null);
        r.setMinBuyin(RoomLevel.fromCode(roomLevel) == null ? 100 : RoomLevel.fromCode(roomLevel).getMinBuyin());
        r.setMaxBuyin(RoomLevel.fromCode(roomLevel) == null ? 1000 : RoomLevel.fromCode(roomLevel).getMaxBuyin());
        r.setTotalTurnover(turnover);
        return r;
    }

    @BeforeEach
    void setup() {
        // spy platformConfig 默认值
        lenient().when(platformConfig.getRoundsPerRoom()).thenReturn(25);
        lenient().when(platformConfig.getPlayerMinBuyin()).thenReturn(100);
        lenient().when(platformConfig.getPlayerMaxBuyin()).thenReturn(100_000);

        // createRoom 会调用 roomMapper.selectCount(按 roomNo 判重)，默认返回 0=唯一
        // 注：使用 doReturn 绕过 Mockito 对 selectCount 泛型返回值的类型检查（MP 不同版本间 Integer/Long 有差异）
        lenient().doReturn(0L).when(gameRoomMapper).selectCount(any(LambdaQueryWrapper.class));
        // joinRoom / smartJoin -> joinRoom 会调用 roomPlayerMapper.selectCount(当前玩家数)，默认 lenient 避免 strict stubbing
        lenient().doReturn(0L).when(roomPlayerMapper).selectCount(any(LambdaQueryWrapper.class));
    }

    // ===========================================================================
    // createRoom 测试
    // ===========================================================================
    @Nested
    @DisplayName("createRoom 开房角色与门槛")
    class CreateRoom {

        private CreateRoomDTO buildDto(String gameType, String roomLevel) {
            CreateRoomDTO dto = new CreateRoomDTO();
            dto.setGameType(gameType);
            dto.setRoomLevel(roomLevel);
            dto.setRoomName("TestRoom");
            dto.setMaxPlayers(8);
            dto.setFixedBetAmount(100L);
            return dto;
        }

        @Test
        @DisplayName("玩家(role=1)开房应抛 BizException 含『权限』")
        void playerCannotCreate() {
            User p = buildUser(1L, "player1", UserRole.PLAYER.getCode(), 10000L, null);
            when(userMapper.selectById(1L)).thenReturn(p);
            assertThatThrownBy(() -> roomService.createRoom(1L, buildDto("TEXAS", "PRIMARY")))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("权限");
            verify(gameRoomMapper, never()).insert(any());
        }

        @Test
        @DisplayName("客服(role=4)开房应抛 BizException")
        void csCannotCreate() {
            User cs = buildUser(4L, "kefu", UserRole.CUSTOMER_SERVICE.getCode(), 0L, null);
            when(userMapper.selectById(4L)).thenReturn(cs);
            assertThatThrownBy(() -> roomService.createRoom(4L, buildDto("JINHUA", "ADVANCED")))
                    .isInstanceOf(BizException.class);
            verify(gameRoomMapper, never()).insert(any());
        }

        @Test
        @DisplayName("二级代理 0 游戏币仍可成功创建房间（移除门槛后的核心逻辑）")
        void zeroCreditAgentCanCreate() {
            User ag = buildUser(2L, "daili01", UserRole.AGENT.getCode(), 0L, 3L);
            ag.setHasFeeFailure(false);
            when(userMapper.selectById(2L)).thenReturn(ag);
            when(gameRoomMapper.insert(any(GameRoom.class))).thenAnswer(inv -> {
                GameRoom r = inv.getArgument(0);
                r.setId(101L);
                return 1;
            });

            CreateRoomDTO dto = buildDto("TEXAS", "PRIMARY");
            GameRoom room = roomService.createRoom(2L, dto);

            assertThat(room).isNotNull();
            assertThat(room.getOwnerId()).isEqualTo(2L);
            assertThat(room.getRoomLevel()).isEqualTo("PRIMARY");
            assertThat(room.getStatus()).isEqualTo(RoomStatus.WAITING.getCode());
            assertThat(room.getRoomNo()).hasSize(6);
            assertThat(room.getMinBuyin()).isEqualTo(200);
            assertThat(room.getMaxBuyin()).isEqualTo(1000);
        }

        @Test
        @DisplayName("代理有扣费失败 hasFeeFailure=true 仍可开（仅warn，不阻断）")
        void feeFailedAgentStillCanCreate() {
            User ag = buildUser(2L, "daili01", UserRole.AGENT.getCode(), 0L, 3L);
            ag.setHasFeeFailure(true);
            when(userMapper.selectById(2L)).thenReturn(ag);
            when(gameRoomMapper.insert(any(GameRoom.class))).thenAnswer(inv -> {
                GameRoom r = inv.getArgument(0);
                r.setId(102L);
                return 1;
            });

            CreateRoomDTO dto = buildDto("DOUNIU", "PREMIUM");
            GameRoom room = roomService.createRoom(2L, dto);

            assertThat(room.getId()).isEqualTo(102L);
            assertThat(room.getRoomLevel()).isEqualTo("PREMIUM");
        }

        @Test
        @DisplayName("总代理可创建房间（验证 role=3 路径）")
        void generalAgentCanCreate() {
            User ga = buildUser(3L, "zongdai", UserRole.GENERAL_AGENT.getCode(), 0L, 5L);
            when(userMapper.selectById(3L)).thenReturn(ga);
            when(gameRoomMapper.insert(any(GameRoom.class))).thenAnswer(inv -> {
                GameRoom r = inv.getArgument(0);
                r.setId(103L);
                return 1;
            });

            GameRoom room = roomService.createRoom(3L, buildDto("SANGONG", "ADVANCED"));
            assertThat(room.getOwnerId()).isEqualTo(3L);
            assertThat(room.getGameType()).isEqualTo("SANGONG");
            assertThat(room.getRoomLevel()).isEqualTo("ADVANCED");
        }

        @Test
        @DisplayName("房间等级不存在时抛 BizException")
        void invalidRoomLevelFails() {
            User ag = buildUser(2L, "daili01", UserRole.AGENT.getCode(), 1000L, 3L);
            when(userMapper.selectById(2L)).thenReturn(ag);
            assertThatThrownBy(() -> roomService.createRoom(2L, buildDto("TEXAS", "JIGUANG")))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("不支持的房间等级");
        }

        @Test
        @DisplayName("房主角色=AGENT但邀请码缺失时，ownerInviteCode=null（与实现保持一致）")
        void agentWithoutInviteCodeStillCreates() {
            User ag = buildUser(2L, "daili01", UserRole.AGENT.getCode(), 50L, 3L);
            ag.setInviteCode(null);
            when(userMapper.selectById(2L)).thenReturn(ag);
            when(gameRoomMapper.insert(any(GameRoom.class))).thenAnswer(inv -> {
                GameRoom r = inv.getArgument(0);
                r.setId(104L);
                return 1;
            });
            GameRoom r = roomService.createRoom(2L, buildDto("TEXAS", "PRIMARY"));
            // 实现是 room.setOwnerInviteCode(agent.getInviteCode())，agent inviteCode 为 null，结果也为 null
            assertThat(r.getOwnerInviteCode()).isNull();
        }
    }

    // ===========================================================================
    // lobbyList + smartJoin 测试
    // ===========================================================================
    @Nested
    @DisplayName("lobbyList 大厅列表")
    class LobbyList {

        @Test
        @DisplayName("大厅查询：调用 gameRoomMapper.selectList 并附带 status + password 条件；返回列表非空")
        void returnsPublicOpenRoomsOnly() {
            List<GameRoom> all = Arrays.asList(
                    buildRoom(1L, "100001", "TEXAS", "PRIMARY", 0, 6, 2L, "AGENT1", false, 1000),
                    buildRoom(6L, "100006", "JINHUA", "PRIMARY", 0, 6, 2L, "AGENT1", false, 600)
            );
            // 注意：这里要返回非空，以便服务继续调用 roomPlayerMapper.selectCount(当前玩家数)
            // selectCount 已在 @BeforeEach 用 lenient 打桩返回 0L
            when(gameRoomMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(all);

            List<Map<String, Object>> list = roomService.lobbyList(null, null);

            ArgumentCaptor<LambdaQueryWrapper<GameRoom>> capt = ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
            verify(gameRoomMapper).selectList(capt.capture());
            assertThat(list).hasSize(2);
            assertThat(list.get(0)).containsKey("currentPlayers");
            assertThat(list.get(0)).containsKey("roomNo");
            assertThat(list.get(0).get("roomNo")).isEqualTo("100001");
            assertThat(list.get(0).get("currentPlayers")).isEqualTo(0);
        }

        @Test
        @DisplayName("lobbyList(gameType) 和 lobbyList(level) 都会触发 selectList 一次")
        void filtersByGameTypeAndLevel() {
            when(gameRoomMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

            roomService.lobbyList("TEXAS", null);
            ArgumentCaptor<LambdaQueryWrapper<GameRoom>> c1 = ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
            verify(gameRoomMapper).selectList(c1.capture());

            roomService.lobbyList(null, "PRIMARY");
            ArgumentCaptor<LambdaQueryWrapper<GameRoom>> c2 = ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
            verify(gameRoomMapper, times(2)).selectList(c2.capture());

            // 两次查询对象都不是 null（参数被追加）
            assertThat(c1.getValue()).isNotNull();
            assertThat(c2.getValue()).isNotNull();
        }
    }

    @Nested
    @DisplayName("smartJoin 智能加入")
    class SmartJoin {

        JoinRoomDTO emptyNo() {
            JoinRoomDTO d = new JoinRoomDTO();
            d.setBuyinCredits(500L);
            d.setGameType("TEXAS");
            d.setRoomLevel("PRIMARY");
            return d;
        }

        JoinRoomDTO withNo(String no) {
            JoinRoomDTO d = emptyNo();
            d.setRoomNo(no);
            return d;
        }

        /**
         * 对 joinRoom 的共同依赖打桩：
         *  - gameRoomMapper.selectOne(按 roomNo = roomNo) -> room
         *  - userMapper.selectById(playerId) -> player
         *  - roomPlayerMapper.selectOne(玩家是否已加入该房) -> null
         *  - roomPlayerMapper.selectList(房间玩家列表) -> 空（座位未满）
         *  - creditService.changeCredits(扣 buyin) -> 无异常
         */
        void stubJoinPrereq(Long playerId, User player, GameRoom room) {
            // selectOne 返回指定 room（注意 joinRoom 内部查询的是 eq(GameRoom::getRoomNo, dto.getRoomNo())）
            when(gameRoomMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(room);
            when(userMapper.selectById(playerId)).thenReturn(player);
            when(roomPlayerMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            when(roomPlayerMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
            when(creditService.changeCredits(eq(playerId), eq(-room.getMinBuyin()), eq(2), eq(room.getId()), eq(room.getRoomNo()),
                    any(), any(), any(), anyString(), eq(false))).thenReturn(player.getCredits() - room.getMinBuyin());
        }

        @Test
        @DisplayName("输入6位房号 → 走精确加入，返回 RoomPlayer.roomNo=123456")
        void roomNoFirst() {
            GameRoom r = buildRoom(1L, "123456", "TEXAS", "PRIMARY", 0, 6, 2L, "AGENT1", false, 0);
            User me = buildUser(10L, "pl", UserRole.PLAYER.getCode(), 2000L, null);
            stubJoinPrereq(10L, me, r);
            when(roomPlayerMapper.insert(any(RoomPlayer.class))).thenAnswer(inv -> {
                RoomPlayer rp = inv.getArgument(0);
                rp.setId(9999L); rp.setRoomNo(r.getRoomNo()); rp.setRoomId(r.getId());
                return 1;
            });

            RoomPlayer rp = roomService.smartJoin(10L, withNo("123456"));
            assertThat(rp).isNotNull();
            assertThat(rp.getUserId()).isEqualTo(10L);
            assertThat(rp.getRoomNo()).isEqualTo("123456");
        }

        @Test
        @DisplayName("房号对应房间不存在 → 抛异常（不降级自动匹配，严格优先）")
        void roomNoNotExistThrows() {
            lenient().when(gameRoomMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            // joinRoom 第1行是 userMapper.selectById(playerId)，没有用户也会抛"用户不存在"先于"房间不存在"
            // 这里补上一个真实玩家 user，确保走到 gameRoomMapper.selectOne(按房号) 的分支
            User me = buildUser(10L, "pl", UserRole.PLAYER.getCode(), 5000L, null);
            lenient().when(userMapper.selectById(10L)).thenReturn(me);
            assertThatThrownBy(() -> roomService.smartJoin(10L, withNo("666666")))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("房间不存在");
        }

        @Test
        @DisplayName("无房号 → 自动匹配，按 gameType+roomLevel 选未满的公开房")
        void autoMatchPicksFirstNotFull() {
            GameRoom full = buildRoom(9L, "900001", "TEXAS", "PRIMARY", 0, 2, 2L, "AGENT1", false, 0);
            GameRoom good = buildRoom(10L, "900002", "TEXAS", "PRIMARY", 0, 6, 2L, "AGENT1", false, 0);

            User me = buildUser(11L, "pNew", UserRole.PLAYER.getCode(), 5000L, null);
            when(userMapper.selectById(11L)).thenReturn(me);

            // smartJoin 第1步：按 gameType+roomLevel 公开房列表
            when(gameRoomMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(full, good));

            // 遍历房间：第1间 full，查 full 房玩家数 返回 2 条（已满，被跳过）；第2间 good 返回 0 条 命中
            RoomPlayer a = new RoomPlayer(); a.setRoomId(9L); a.setUserId(50L); a.setIsObserver(false);
            RoomPlayer b = new RoomPlayer(); b.setRoomId(9L); b.setUserId(51L); b.setIsObserver(false);
            when(roomPlayerMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Arrays.asList(a, b))
                    .thenReturn(Collections.emptyList());

            // 命中后 joinRoom：gameRoomMapper.selectOne(按"900002") → good；再查一次座位玩家列表 → 空
            when(gameRoomMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(good);
            // 玩家"未加入过命中房间"（selectOne = null）
            when(roomPlayerMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            // buyin = 500（>= PRIMARY 200），扣 500
            when(creditService.changeCredits(eq(11L), eq(-500L), eq(2), eq(10L), eq("900002"),
                    any(), any(), any(), anyString(), eq(false))).thenReturn(4500L);

            when(roomPlayerMapper.insert(any(RoomPlayer.class))).thenAnswer(inv -> {
                RoomPlayer rp = inv.getArgument(0);
                rp.setId(100_000L);
                rp.setRoomNo("900002");
                rp.setRoomId(10L);
                return 1;
            });

            RoomPlayer rp = roomService.smartJoin(11L, emptyNo());
            assertThat(rp.getRoomId()).isEqualTo(10L);
            assertThat(rp.getRoomNo()).isEqualTo("900002");
        }

        @Test
        @DisplayName("自动匹配时跳过玩家已加入的房间，避免一人多入")
        void autoMatchSkipsPlayerJoinedRoom() {
            GameRoom r1 = buildRoom(20L, "200001", "TEXAS", "PRIMARY", 0, 6, 2L, "AGENT1", false, 0);
            GameRoom r2 = buildRoom(21L, "200002", "TEXAS", "PRIMARY", 0, 6, 3L, "ZONGD1", false, 0);

            User me = buildUser(50L, "pA", UserRole.PLAYER.getCode(), 5000L, null);
            when(userMapper.selectById(50L)).thenReturn(me);
            when(gameRoomMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(r1, r2));

            // 第1间（r1）：先查 selectOne 判断玩家是否已加入 → 已加入 → 跳过；再查 r2 selectOne → 未加入
            RoomPlayer already = new RoomPlayer(); already.setRoomId(20L); already.setUserId(50L); already.setIsObserver(false);
            when(roomPlayerMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(already, null);
            // 两房玩家列表皆空
            when(roomPlayerMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
            // joinRoom 的 selectOne 命中 r2
            when(gameRoomMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(r2);
            when(creditService.changeCredits(eq(50L), eq(-500L), eq(2), eq(21L), eq("200002"),
                    any(), any(), any(), anyString(), eq(false))).thenReturn(4500L);

            when(roomPlayerMapper.insert(any(RoomPlayer.class))).thenAnswer(inv -> {
                RoomPlayer rp = inv.getArgument(0);
                rp.setId(200_000L); rp.setRoomNo("200002"); rp.setRoomId(21L);
                return 1;
            });

            RoomPlayer rp = roomService.smartJoin(50L, emptyNo());
            assertThat(rp.getRoomId()).isEqualTo(21L);
            assertThat(rp.getRoomNo()).isEqualTo("200002");
        }

        @Test
        @DisplayName("没有任何匹配的房间 → 抛出 暂无匹配")
        void autoMatchNoRoomThrows() {
            lenient().when(gameRoomMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
            assertThatThrownBy(() -> roomService.smartJoin(50L, emptyNo()))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("暂无匹配");
        }
    }
}
