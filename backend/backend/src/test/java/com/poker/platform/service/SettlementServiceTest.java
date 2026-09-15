package com.poker.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.poker.platform.config.PlatformConfig;
import com.poker.platform.entity.*;
import com.poker.platform.enums.RoomStatus;
import com.poker.platform.enums.UserRole;
import com.poker.platform.exception.BizException;
import com.poker.platform.mapper.*;
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

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SettlementService 结算服务单元测试")
class SettlementServiceTest {

    @Mock private GameRoomMapper roomMapper;
    @Mock private RoomPlayerMapper roomPlayerMapper;
    @Mock private GameRoundMapper roundMapper;
    @Mock private UserMapper userMapper;
    @Mock private CreditLogMapper creditLogMapper;
    @Mock private AgentWaterFeeLogMapper waterFeeLogMapper;
    @Mock private GeneralAgentCommissionMapper commissionMapper;
    @Mock private RakeRebateLogMapper rakeRebateLogMapper;
    @Spy private PlatformConfig platformConfig = new PlatformConfig();
    @Mock private CreditService creditService;
    @InjectMocks private SettlementService settlementService;

    @BeforeEach
    void setup() {
        // 单局抽水 3%、总局数 25
        lenient().doReturn(3).when(platformConfig).getRakePercent();
        lenient().doReturn(25).when(platformConfig).getRoundsPerRoom();
        // 开房抽佣（水费）与多级抽成依赖 PlatformConfig 默认值：
        // 水费 总代2% / 一级1.5% / 二级1%
        // 抽成 二级抽客户1% / 一级抽二级0.5% / 总代抽一级0.5% / 总代兜底1% / 一级兜底1.5%
    }

    static User user(Long id, String name, int role, long credits, Long parentId) {
        User u = new User();
        u.setId(id); u.setUsername(name); u.setRole(role);
        u.setCredits(credits); u.setParentId(parentId);
        return u;
    }

    static GameRoom room(Long id, String no, long owner, long turnover, long rake) {
        GameRoom r = new GameRoom();
        r.setId(id); r.setRoomNo(no); r.setOwnerId(owner);
        r.setTotalTurnover(turnover); r.setTotalRake(rake);
        r.setStatus(RoomStatus.PLAYING.getCode());
        return r;
    }

    static RoomPlayer rp(long userId, String uname, long cur, boolean ob) {
        RoomPlayer p = new RoomPlayer();
        p.setUserId(userId); p.setUsername(uname);
        p.setCurrentCredits(cur); p.setIsObserver(ob);
        return p;
    }

    // =========================================================================
    // settleRoom 总局结算：按房主角色抽佣 + 多级推广抽成
    // =========================================================================
    @Nested
    @DisplayName("settleRoom 总局结算")
    class SettleRoom {

        @Test
        @DisplayName("二级代理开房，上级为总代（无一级）：水费1%+房主抽成1%+总代兜底抽1%")
        void secondaryAgentOwner_GAFallback() {
            User owner = user(2L, "daili01", UserRole.AGENT.getCode(), 1000L, 3L);
            User ga    = user(3L, "zongdai", UserRole.GENERAL_AGENT.getCode(), 5000L, 5L);
            GameRoom r = room(1L, "123456", 2L, 10000L, 300L);

            List<RoomPlayer> players = Arrays.asList(
                    rp(5L, "p1", 500L, false),
                    rp(6L, "p2", 400L, false),
                    rp(7L, "obs1", 0L, true)
            );
            User p1 = user(5L, "p1", 1, 0L, 2L);
            User p2 = user(6L, "p2", 1, 100L, 2L);

            when(roomMapper.selectById(1L)).thenReturn(r);
            when(roomPlayerMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(players);
            when(userMapper.selectById(2L)).thenReturn(owner);
            when(userMapper.selectById(3L)).thenReturn(ga);
            when(userMapper.selectById(5L)).thenReturn(p1);
            when(userMapper.selectById(6L)).thenReturn(p2);

            Map<String, Object> res = settlementService.settleRoom(1L);

            assertThat(res.get("roomNo")).isEqualTo("123456");
            assertThat(res.get("totalTurnover")).isEqualTo(10000L);
            // 水费 1% = 100；房主抽成 1% = 100；净成本 0
            assertThat(res.get("ownerDeducted")).isEqualTo(100L);
            assertThat(res.get("ownerRebate")).isEqualTo(100L);
            assertThat(res.get("ownerNetCost")).isEqualTo(0L);
            assertThat(res.get("billStatus")).isEqualTo("SUCCESS");
            assertThat(res.get("waterFee")).isEqualTo(100L);
            assertThat(res.get("waterFeeStatus")).isEqualTo("SUCCESS");
            // 总代兜底抽二级 1% = 100
            assertThat(res.get("generalAgentCommission")).isEqualTo(100L);

            // 房主余额 1000 - 100(水费) + 100(抽成) = 1000
            assertThat(owner.getCredits()).isEqualTo(1000L);
            // 总代余额 5000 + 100 = 5100
            assertThat(ga.getCredits()).isEqualTo(5100L);

            // 玩家退还：p1 0+500=500, p2 100+400=500
            assertThat(p1.getCredits()).isEqualTo(500L);
            assertThat(p2.getCredits()).isEqualTo(500L);

            // 返佣账单写入
            ArgumentCaptor<RakeRebateLog> rebateCapt = ArgumentCaptor.forClass(RakeRebateLog.class);
            verify(rakeRebateLogMapper).insert(rebateCapt.capture());
            RakeRebateLog rl = rebateCapt.getValue();
            assertThat(rl.getRoomNo()).isEqualTo("123456");
            assertThat(rl.getDeductedAmount()).isEqualTo(100L);
            assertThat(rl.getRebateAmount()).isEqualTo(100L);
            assertThat(rl.getNetCost()).isEqualTo(0L);
            assertThat(rl.getStatus()).isEqualTo(0);
            assertThat(rl.getFailReason()).isNull();

            // 水费日志：水费金额=100，已还
            ArgumentCaptor<AgentWaterFeeLog> feeCapt = ArgumentCaptor.forClass(AgentWaterFeeLog.class);
            verify(waterFeeLogMapper).insert(feeCapt.capture());
            AgentWaterFeeLog fl = feeCapt.getValue();
            assertThat(fl.getFeeAmount()).isEqualTo(100L);
            assertThat(fl.getRepaid()).isTrue();
            assertThat(fl.getStatus()).isEqualTo(0);

            // 总代抽成写入分润表
            ArgumentCaptor<GeneralAgentCommission> gcCapt = ArgumentCaptor.forClass(GeneralAgentCommission.class);
            verify(commissionMapper).insert(gcCapt.capture());
            GeneralAgentCommission gc = gcCapt.getValue();
            assertThat(gc.getGeneralAgentId()).isEqualTo(3L);
            assertThat(gc.getCommissionAmount()).isEqualTo(100L);

            assertThat(r.getStatus()).isEqualTo(RoomStatus.SETTLED.getCode());
            assertThat(r.getTotalWaterFee()).isEqualTo(100L);
        }

        @Test
        @DisplayName("二级代理开房，上级一级代理，再上级总代：水费1%+房主1%+一级0.5%+总代0.5%")
        void secondaryAgentOwner_fullChain() {
            User owner = user(2L, "daili01", UserRole.AGENT.getCode(), 1000L, 6L);
            User primary = user(6L, "hexindaili", UserRole.PRIMARY_AGENT.getCode(), 3000L, 3L);
            User ga = user(3L, "zongdai", UserRole.GENERAL_AGENT.getCode(), 5000L, 5L);
            GameRoom r = room(1L, "123456", 2L, 10000L, 300L);

            when(roomMapper.selectById(1L)).thenReturn(r);
            when(roomPlayerMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
            when(userMapper.selectById(2L)).thenReturn(owner);
            when(userMapper.selectById(6L)).thenReturn(primary);
            when(userMapper.selectById(3L)).thenReturn(ga);

            Map<String, Object> res = settlementService.settleRoom(1L);

            assertThat(res.get("ownerDeducted")).isEqualTo(100L);  // 水费 1%
            assertThat(res.get("ownerRebate")).isEqualTo(100L);    // 房主抽成 1%
            assertThat(res.get("ownerNetCost")).isEqualTo(0L);
            assertThat(res.get("primaryCommission")).isEqualTo(50L); // 一级抽二级 0.5%
            assertThat(res.get("generalAgentCommission")).isEqualTo(50L); // 总代抽一级 0.5%

            // 房主 1000 - 100 + 100 = 1000
            assertThat(owner.getCredits()).isEqualTo(1000L);
            // 一级代理 3000 + 50 = 3050
            assertThat(primary.getCredits()).isEqualTo(3050L);
            // 总代 5000 + 50 = 5050
            assertThat(ga.getCredits()).isEqualTo(5050L);
        }

        @Test
        @DisplayName("一级代理开房：水费1.5%+房主兜底抽1.5%+总代抽0.5%")
        void primaryAgentOwner() {
            User owner = user(6L, "hexindaili", UserRole.PRIMARY_AGENT.getCode(), 2000L, 3L);
            User ga = user(3L, "zongdai", UserRole.GENERAL_AGENT.getCode(), 5000L, 5L);
            GameRoom r = room(2L, "123457", 6L, 10000L, 200L);

            when(roomMapper.selectById(2L)).thenReturn(r);
            when(roomPlayerMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
            when(userMapper.selectById(6L)).thenReturn(owner);
            when(userMapper.selectById(3L)).thenReturn(ga);

            Map<String, Object> res = settlementService.settleRoom(2L);

            assertThat(res.get("ownerDeducted")).isEqualTo(150L); // 水费 1.5%
            assertThat(res.get("ownerRebate")).isEqualTo(150L);   // 房主兜底抽 1.5%
            assertThat(res.get("ownerNetCost")).isEqualTo(0L);
            assertThat(res.get("generalAgentCommission")).isEqualTo(50L); // 总代抽一级 0.5%

            // 房主 2000 - 150 + 150 = 2000
            assertThat(owner.getCredits()).isEqualTo(2000L);
            // 总代 5000 + 50 = 5050
            assertThat(ga.getCredits()).isEqualTo(5050L);
        }

        @Test
        @DisplayName("总代理开房：水费2%，无抽成，无分润")
        void generalAgentAsOwner() {
            User owner = user(3L, "zongdai", UserRole.GENERAL_AGENT.getCode(), 500L, 5L);
            GameRoom r = room(3L, "123458", 3L, 5000L, 200L);

            when(roomMapper.selectById(3L)).thenReturn(r);
            when(roomPlayerMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
            when(userMapper.selectById(3L)).thenReturn(owner);

            Map<String, Object> res = settlementService.settleRoom(3L);
            assertThat(res.get("ownerDeducted")).isEqualTo(100L); // 5000 * 2%
            assertThat(res.get("ownerRebate")).isEqualTo(0L);
            assertThat(res.get("ownerNetCost")).isEqualTo(100L);
            assertThat(res.get("generalAgentCommission")).isEqualTo(0L);

            // owner 500 - 100 = 400
            assertThat(owner.getCredits()).isEqualTo(400L);
            assertThat(owner.getHasFeeFailure()).isNull();

            verify(commissionMapper, never()).insert(any());
        }

        @Test
        @DisplayName("房主信用分不足：标记扣费失败+hasFeeFailure=true，余额不变")
        void ownerCreditInsufficientMarksFailure() {
            User owner = user(2L, "daili01", UserRole.AGENT.getCode(), 50L, 3L);
            User ga    = user(3L, "zongdai", UserRole.GENERAL_AGENT.getCode(), 0L, 5L);
            GameRoom r = room(4L, "123459", 2L, 10000L, 300L);

            when(roomMapper.selectById(4L)).thenReturn(r);
            when(roomPlayerMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
            when(userMapper.selectById(2L)).thenReturn(owner);
            when(userMapper.selectById(3L)).thenReturn(ga);

            Map<String, Object> res = settlementService.settleRoom(4L);
            // 水费 1% = 100，余额 50 不足 → FAILED
            assertThat(res.get("billStatus")).isEqualTo("FAILED");
            assertThat(res.get("waterFeeStatus")).isEqualTo("FAILED");
            String failReason = (String) res.get("billFailReason");
            assertThat(failReason).contains("房主信用分不足");
            assertThat(failReason).contains("当前=50");
            assertThat(failReason).contains("应扣水费=100");

            // 余额不变（水费失败，但房主抽成仍发放 1% = 100）
            assertThat(owner.getHasFeeFailure()).isTrue();
            assertThat(res.get("ownerDeducted")).isEqualTo(100L);
            // 房主抽成仍发：50 + 100 = 150
            assertThat(res.get("ownerRebate")).isEqualTo(100L);
            assertThat(owner.getCredits()).isEqualTo(150L);

            // 总代兜底抽成仍触发 1% = 100
            assertThat(res.get("generalAgentCommission")).isEqualTo(100L);
            assertThat(ga.getCredits()).isEqualTo(100L);

            ArgumentCaptor<RakeRebateLog> rebateCapt = ArgumentCaptor.forClass(RakeRebateLog.class);
            verify(rakeRebateLogMapper).insert(rebateCapt.capture());
            assertThat(rebateCapt.getValue().getStatus()).isEqualTo(1);
            assertThat(rebateCapt.getValue().getFailReason()).isNotNull();
        }

        @Test
        @DisplayName("总流水=0：不扣不减，金额=0 billStatus=SUCCESS")
        void zeroTurnoverNoDeductNoRebate() {
            User owner = user(2L, "daili01", UserRole.AGENT.getCode(), 0L, 3L);
            GameRoom r = room(5L, "000001", 2L, 0L, 0L);

            when(roomMapper.selectById(5L)).thenReturn(r);
            when(roomPlayerMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
            when(userMapper.selectById(2L)).thenReturn(owner);

            Map<String, Object> res = settlementService.settleRoom(5L);
            assertThat(res.get("ownerDeducted")).isEqualTo(0L);
            assertThat(res.get("ownerRebate")).isEqualTo(0L);
            assertThat(res.get("ownerNetCost")).isEqualTo(0L);
            assertThat(res.get("billStatus")).isEqualTo("SUCCESS");
            assertThat(owner.getHasFeeFailure()).isNull();
        }

        @Test
        @DisplayName("房间不存在 → 抛 BizException")
        void roomNotExistThrows() {
            when(roomMapper.selectById(99L)).thenReturn(null);
            assertThatThrownBy(() -> settlementService.settleRoom(99L))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("房间不存在");
        }

        @Test
        @DisplayName("二级代理开房但上级非总代/一级代理：不触发分润，房主自己仍抽1%")
        void agentOwnerParentNotAgentSkipCommission() {
            User owner = user(2L, "dailiX", UserRole.AGENT.getCode(), 2000L, 5L); // 上级是超管5
            GameRoom r = room(6L, "200001", 2L, 30000L, 900L);

            when(roomMapper.selectById(6L)).thenReturn(r);
            when(roomPlayerMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
            when(userMapper.selectById(2L)).thenReturn(owner);

            Map<String, Object> res = settlementService.settleRoom(6L);
            // 水费 1% = 300；房主抽成 1% = 300；净 0；无分润（父角色=5 超管）
            assertThat(res.get("ownerDeducted")).isEqualTo(300L);
            assertThat(res.get("ownerRebate")).isEqualTo(300L);
            assertThat(res.get("generalAgentCommission")).isEqualTo(0L);
            verify(commissionMapper, never()).insert(any());
        }
    }
}
