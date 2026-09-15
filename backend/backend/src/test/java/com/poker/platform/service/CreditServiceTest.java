package com.poker.platform.service;

import com.poker.platform.entity.CreditLog;
import com.poker.platform.entity.User;
import com.poker.platform.enums.UserRole;
import com.poker.platform.exception.BizException;
import com.poker.platform.mapper.CreditLogMapper;
import com.poker.platform.mapper.UserMapper;
import com.poker.platform.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreditService 游戏币核心服务单元测试")
class CreditServiceTest {

    @Mock private UserMapper userMapper;
    @Mock private CreditLogMapper creditLogMapper;
    @InjectMocks private CreditService creditService;

    @BeforeEach
    void setup() { UserContext.clear(); }

    @AfterEach
    void cleanup() { UserContext.clear(); }

    static User user(Long id, String name, int role, long credits, Long parentId) {
        User u = new User();
        u.setId(id); u.setUsername(name); u.setRole(role); u.setCredits(credits); u.setParentId(parentId);
        return u;
    }

    // =========================================================================
    // changeCredits 原子变动
    // =========================================================================
    @Nested
    @DisplayName("changeCredits 原子变动")
    class ChangeCredits {

        @Test
        @DisplayName("正加：余额 1000 + 500 = 1500，流水 type=3")
        void positiveIncrement() {
            User u = user(1L, "p1", 1, 1000L, 2L);
            when(userMapper.selectById(1L)).thenReturn(u);
            Long after = creditService.changeCredits(1L, 500, 3, 1L, "123456", 1L,
                    null, null, "test", false);
            assertThat(after).isEqualTo(1500L);
            assertThat(u.getCredits()).isEqualTo(1500L);
            verify(userMapper).updateById(u);
            ArgumentCaptor<CreditLog> capt = ArgumentCaptor.forClass(CreditLog.class);
            verify(creditLogMapper).insert(capt.capture());
            CreditLog l = capt.getValue();
            assertThat(l.getUserId()).isEqualTo(1L);
            assertThat(l.getChangeType()).isEqualTo(3);
            assertThat(l.getChangeValue()).isEqualTo(500L);
            assertThat(l.getBeforeValue()).isEqualTo(1000L);
            assertThat(l.getAfterValue()).isEqualTo(1500L);
            assertThat(l.getRoomId()).isEqualTo(1L);
            assertThat(l.getRoomNo()).isEqualTo("123456");
            assertThat(l.getRoundId()).isEqualTo(1L);
            assertThat(l.getRemark()).isEqualTo("test");
        }

        @Test
        @DisplayName("负减 allowNegative=false 不够扣 → 抛 BizException，不更新余额")
        void insufficientNotAllowThrows() {
            User u = user(1L, "p1", 1, 50L, 2L);
            when(userMapper.selectById(1L)).thenReturn(u);
            assertThatThrownBy(() -> creditService.changeCredits(1L, -200, 9, null, null, null,
                    2L, "daili01", "扣", false))
                    .isInstanceOf(BizException.class).hasMessageContaining("游戏币不足");
            assertThat(u.getCredits()).isEqualTo(50L);
            verify(userMapper, never()).updateById(any());
            verify(creditLogMapper, never()).insert(any());
        }

        @Test
        @DisplayName("负减 allowNegative=true 允许负数，不抛")
        void allowNegativeTrueCanGoBelowZero() {
            User u = user(5L, "p1", 1, 50L, 2L);
            when(userMapper.selectById(5L)).thenReturn(u);
            Long after = creditService.changeCredits(5L, -200, 2, null, null, null,
                    2L, "daili01", "代理调整", true);
            assertThat(after).isEqualTo(-150L);
        }

        @Test
        @DisplayName("changeValue=0 直接返回 null，不写库")
        void zeroReturnNullNoop() {
            assertThat(creditService.changeCredits(1L, 0, 3, null, null, null,
                    null, null, "noop", false)).isNull();
            verify(userMapper, never()).selectById(any());
        }

        @Test
        @DisplayName("用户不存在 → BizException")
        void userNotExistThrows() {
            when(userMapper.selectById(999L)).thenReturn(null);
            assertThatThrownBy(() -> creditService.changeCredits(999L, 1, 3,
                    null, null, null, null, null, "x", false))
                    .isInstanceOf(BizException.class).hasMessageContaining("用户不存在");
        }
    }

    // =========================================================================
    // playerTransfer P2P
    // =========================================================================
    @Nested
    @DisplayName("playerTransfer P2P 玩家互赠")
    class PlayerTransfer {
        @Test
        @DisplayName("正流程：p1(2000) → p2(100) 500：p1=1500 p2=600，写入两条流水")
        void happyPath() {
            User from = user(1L, "p1", UserRole.PLAYER.getCode(), 2000L, 2L);
            User to   = user(2L, "p2", UserRole.PLAYER.getCode(), 100L, 3L);
            when(userMapper.selectById(1L)).thenReturn(from);
            when(userMapper.selectById(2L)).thenReturn(to);

            long after = creditService.playerTransfer(1L, "p1", 2L, "p2", 500L, "晚饭");
            assertThat(after).isEqualTo(600L);
            assertThat(from.getCredits()).isEqualTo(1500L);
            assertThat(to.getCredits()).isEqualTo(600L);

            ArgumentCaptor<CreditLog> capt = ArgumentCaptor.forClass(CreditLog.class);
            verify(creditLogMapper, times(2)).insert(capt.capture());
            CreditLog out = capt.getAllValues().get(0);
            CreditLog in  = capt.getAllValues().get(1);
            assertThat(out.getUserId()).isEqualTo(1L);
            assertThat(out.getChangeValue()).isEqualTo(-500L);
            assertThat(out.getChangeType()).isEqualTo(12);
            assertThat(out.getBeforeValue()).isEqualTo(2000L);
            assertThat(out.getAfterValue()).isEqualTo(1500L);
            assertThat(out.getRemark()).contains("晚饭");

            assertThat(in.getUserId()).isEqualTo(2L);
            assertThat(in.getChangeValue()).isEqualTo(+500L);
            assertThat(in.getChangeType()).isEqualTo(12);
            assertThat(in.getBeforeValue()).isEqualTo(100L);
            assertThat(in.getAfterValue()).isEqualTo(600L);
        }

        @Test
        @DisplayName("value <= 0 抛异常")
        void invalidValueThrows() {
            assertThatThrownBy(() -> creditService.playerTransfer(1L, "a", 2L, "b", 0L, null))
                    .isInstanceOf(BizException.class).hasMessageContaining("大于0");
            assertThatThrownBy(() -> creditService.playerTransfer(1L, "a", 2L, "b", -1L, null))
                    .isInstanceOf(BizException.class);
            verify(creditLogMapper, never()).insert(any());
        }

        @Test
        @DisplayName("转出方余额不够时不写入任何流水（事务性，扣完即抛）")
        void fromInsufficientNoInFlow() {
            User from = user(1L, "p1", 1, 100L, 2L);
            when(userMapper.selectById(1L)).thenReturn(from);
            assertThatThrownBy(() -> creditService.playerTransfer(1L, "p1", 2L, "p2", 200L, null))
                    .isInstanceOf(BizException.class);
            verify(creditLogMapper, never()).insert(any());
        }
    }

    // =========================================================================
    // promoteGrant 推广划拨（含虚拟出库）
    // =========================================================================
    @Nested
    @DisplayName("promoteGrant 推广划拨/层级发放")
    class PromoteGrant {

        @Test
        @DisplayName("真实扣除 deductFrom=true：总代理→二级代理 1000，从总代理余额扣 1000")
        void deductFromTrue() {
            User ga = user(3L, "zongdai", UserRole.GENERAL_AGENT.getCode(), 3000L, 5L);
            User ag = user(2L, "daili01", UserRole.AGENT.getCode(), 500L, 3L);
            when(userMapper.selectById(3L)).thenReturn(ga);
            when(userMapper.selectById(2L)).thenReturn(ag);

            long after = creditService.promoteGrant(3L, "zongdai", 2L, "daili01",
                    UserRole.GENERAL_AGENT.getCode(), 1000L, "周度扶持", true);

            assertThat(after).isEqualTo(1500L);
            assertThat(ga.getCredits()).isEqualTo(2000L);
            assertThat(ag.getCredits()).isEqualTo(1500L);

            ArgumentCaptor<CreditLog> capt = ArgumentCaptor.forClass(CreditLog.class);
            verify(creditLogMapper, times(2)).insert(capt.capture());
            CreditLog out = capt.getAllValues().get(0);
            CreditLog in  = capt.getAllValues().get(1);
            assertThat(out.getUserId()).isEqualTo(3L);
            assertThat(out.getChangeValue()).isEqualTo(-1000L);
            assertThat(out.getBeforeValue()).isEqualTo(3000L);
            assertThat(out.getAfterValue()).isEqualTo(2000L); // 真实扣
            assertThat(in.getUserId()).isEqualTo(2L);
            assertThat(in.getChangeValue()).isEqualTo(+1000L);
        }

        @Test
        @DisplayName("虚拟出库 deductFrom=false：客服→总代理 5000，客服余额不变但仍记出账流水")
        void deductFromFalseVirtualOutbound() {
            User cs = user(4L, "kefu", UserRole.CUSTOMER_SERVICE.getCode(), 0L, null);
            User ga = user(3L, "zongdai", UserRole.GENERAL_AGENT.getCode(), 100L, 5L);
            when(userMapper.selectById(4L)).thenReturn(cs);
            when(userMapper.selectById(3L)).thenReturn(ga);

            long after = creditService.promoteGrant(4L, "kefu", 3L, "zongdai",
                    UserRole.CUSTOMER_SERVICE.getCode(), 5000L, "月度调拨", false);

            assertThat(after).isEqualTo(5100L);
            assertThat(cs.getCredits()).isEqualTo(0L);   // 虚拟出库，不扣
            assertThat(ga.getCredits()).isEqualTo(5100L); // 确实加到接收方

            ArgumentCaptor<CreditLog> capt = ArgumentCaptor.forClass(CreditLog.class);
            verify(creditLogMapper, times(2)).insert(capt.capture());
            CreditLog out = capt.getAllValues().get(0);
            CreditLog in  = capt.getAllValues().get(1);
            assertThat(out.getUserId()).isEqualTo(4L);
            assertThat(out.getChangeValue()).isEqualTo(-5000L);
            assertThat(out.getBeforeValue()).isEqualTo(0L);
            assertThat(out.getAfterValue()).isEqualTo(0L);  // 余额不变（虚拟）
            assertThat(out.getRemark()).contains("虚拟出库");
            assertThat(in.getUserId()).isEqualTo(3L);
            assertThat(in.getChangeValue()).isEqualTo(+5000L);
        }

        @Test
        @DisplayName("真实扣除 转出方余额不够 → 抛异常，收款方未动")
        void deductFromTrueButInsufficientThrows() {
            User ga = user(3L, "zongdai", UserRole.GENERAL_AGENT.getCode(), 500L, 5L);
            User ag = user(2L, "daili01", UserRole.AGENT.getCode(), 0L, 3L);
            when(userMapper.selectById(3L)).thenReturn(ga);
            when(userMapper.selectById(2L)).thenReturn(ag);

            assertThatThrownBy(() -> creditService.promoteGrant(3L, "zongdai", 2L, "daili01",
                    UserRole.GENERAL_AGENT.getCode(), 2000L, "x", true))
                    .isInstanceOf(BizException.class).hasMessageContaining("游戏币不足");
            assertThat(ag.getCredits()).isEqualTo(0L); // 接收方未变
            verify(creditLogMapper, never()).insert(any());
        }

        @Test
        @DisplayName("value 非法抛出异常")
        void invalidValueThrows() {
            assertThatThrownBy(() -> creditService.promoteGrant(1L, "a", 2L, "b", 1,
                    0, null, true)).isInstanceOf(BizException.class);
        }
    }

    // =========================================================================
    // adminAdjust 调用后根据当前上下文角色选择正确 type
    // =========================================================================
    @Nested
    @DisplayName("adminAdjust 按上下文字段区分 changeType（超管=8 客服=7）")
    class AdminAdjust {
        @Test
        @DisplayName("超管调整：changeType=8")
        void superAdminUsesType8() {
            User target = user(6L, "t", UserRole.PLAYER.getCode(), 100L, 2L);
            when(userMapper.selectById(6L)).thenReturn(target);
            UserContext.set(5L, "admin", UserRole.SUPER_ADMIN.getCode());
            Long after = creditService.adminAdjust(6L, +1000, "超管补");
            assertThat(after).isEqualTo(1100L);
            ArgumentCaptor<CreditLog> capt = ArgumentCaptor.forClass(CreditLog.class);
            verify(creditLogMapper).insert(capt.capture());
            assertThat(capt.getValue().getChangeType()).isEqualTo(8);
            assertThat(capt.getValue().getOperatorId()).isEqualTo(5L);
            assertThat(capt.getValue().getOperatorName()).isEqualTo("admin");
        }

        @Test
        @DisplayName("客服调整：changeType=7")
        void customerServiceUsesType7() {
            User target = user(6L, "t", UserRole.PLAYER.getCode(), 50L, 2L);
            when(userMapper.selectById(6L)).thenReturn(target);
            UserContext.set(4L, "kefu", UserRole.CUSTOMER_SERVICE.getCode());
            creditService.adminAdjust(6L, -50, "客服扣");
            ArgumentCaptor<CreditLog> capt = ArgumentCaptor.forClass(CreditLog.class);
            verify(creditLogMapper).insert(capt.capture());
            assertThat(capt.getValue().getChangeType()).isEqualTo(7);
            assertThat(capt.getValue().getRemark()).isEqualTo("客服扣");
        }
    }
}
