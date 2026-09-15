package com.poker.platform.controller;

import com.poker.platform.entity.User;
import com.poker.platform.enums.UserRole;
import com.poker.platform.exception.BizException;
import com.poker.platform.mapper.*;
import com.poker.platform.security.UserContext;
import com.poker.platform.service.CreditService;
import com.poker.platform.service.SettlementService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminController 游戏币人工调整 单测")
class AdminControllerCreditAdjustTest {

    @Mock private UserMapper userMapper;
    @Mock private CreditLogMapper creditLogMapper;
    @Mock private AgentWaterFeeLogMapper waterFeeLogMapper;
    @Mock private GeneralAgentCommissionMapper commissionMapper;
    @Mock private RakeRebateLogMapper rakeRebateLogMapper;
    @Mock private GameRoomMapper roomMapper;
    @Mock private GameRoundMapper roundMapper;
    @Mock private PlatformAnnouncementMapper annMapper;
    @Mock private PlatformComplaintMapper complaintMapper;
    @Mock private CreditService creditService;
    @Mock private SettlementService settlementService;
    @InjectMocks private AdminController ctrl;

    @BeforeEach
    void setUp() { UserContext.clear(); }
    @AfterEach
    void tearDown() { UserContext.clear(); }

    static AdminController.CreditAdjustDTO dto(Long uid, Long cv, String remark) {
        AdminController.CreditAdjustDTO d = new AdminController.CreditAdjustDTO();
        d.setUserId(uid); d.setChangeValue(cv); d.setRemark(remark);
        return d;
    }

    @Nested
    @DisplayName("creditAdjust 参数/权限校验")
    class Validations {
        @Test
        @DisplayName("未登录 无角色 → requireRole(4) 抛无权限")
        void notLoginNoPermission() {
            assertThatThrownBy(() -> ctrl.creditAdjust(dto(6L, 100L, "补")))
                    .isInstanceOf(BizException.class).hasMessageContaining("无权限");
        }

        @Test
        @DisplayName("玩家 role=1 < 4 → requireRole(4) 抛无权限")
        void playerNoPermission() {
            UserContext.set(1L, "player", UserRole.PLAYER.getCode());
            assertThatThrownBy(() -> ctrl.creditAdjust(dto(6L, 100L, "补")))
                    .isInstanceOf(BizException.class).hasMessageContaining("无权限");
            verify(creditService, never()).adminAdjust(anyLong(), anyLong(), any());
        }

        @Test
        @DisplayName("二级代理 role=2 < 4 → 仍被拦截")
        void agentNoPermission() {
            UserContext.set(2L, "ag", UserRole.AGENT.getCode());
            assertThatThrownBy(() -> ctrl.creditAdjust(dto(6L, 100L, "补")))
                    .isInstanceOf(BizException.class);
        }

        @Test
        @DisplayName("总代理 role=3 < 4 → 仍被拦截")
        void generalAgentNoPermission() {
            UserContext.set(3L, "ga", UserRole.GENERAL_AGENT.getCode());
            assertThatThrownBy(() -> ctrl.creditAdjust(dto(6L, 100L, "补")))
                    .isInstanceOf(BizException.class);
        }

        @Test
        @DisplayName("客服 role=4 通过权限门槛")
        void customerServiceCanAccess() {
            UserContext.set(4L, "kefu", UserRole.CUSTOMER_SERVICE.getCode());
            User t = new User(); t.setId(6L); t.setCredits(0L); t.setUsername("p1"); t.setRole(1);
            when(userMapper.selectById(6L)).thenReturn(t);
            when(creditService.adminAdjust(eq(6L), eq(100L), any())).thenReturn(100L);
            Long after = ctrl.creditAdjust(dto(6L, 100L, "补币")).getData();
            assertThat(after).isEqualTo(100L);
        }

        @Test
        @DisplayName("超管 role=5 通过权限门槛")
        void superAdminCanAccess() {
            UserContext.set(5L, "admin", UserRole.SUPER_ADMIN.getCode());
            User t = new User(); t.setId(6L); t.setCredits(10L); t.setUsername("p1"); t.setRole(1);
            when(userMapper.selectById(6L)).thenReturn(t);
            when(creditService.adminAdjust(eq(6L), eq(-5L), any())).thenReturn(5L);
            Long after = ctrl.creditAdjust(dto(6L, -5L, "扣点")).getData();
            assertThat(after).isEqualTo(5L);
        }

        @Test
        @DisplayName("userId 为空 → 报错")
        void noUserId() {
            UserContext.set(4L, "kefu", UserRole.CUSTOMER_SERVICE.getCode());
            assertThatThrownBy(() -> ctrl.creditAdjust(dto(null, 100L, "补")))
                    .isInstanceOf(BizException.class).hasMessageContaining("参数无效");
        }

        @Test
        @DisplayName("changeValue=0 → 报错；不调用 service")
        void zeroChangeValueFails() {
            UserContext.set(5L, "admin", UserRole.SUPER_ADMIN.getCode());
            assertThatThrownBy(() -> ctrl.creditAdjust(dto(1L, 0L, "零")))
                    .isInstanceOf(BizException.class).hasMessageContaining("changeValue不能为0");
            verify(creditService, never()).adminAdjust(anyLong(), anyLong(), any());
        }

        @Test
        @DisplayName("remark 为 null → 必填理由报错")
        void nullRemarkFails() {
            UserContext.set(5L, "admin", UserRole.SUPER_ADMIN.getCode());
            assertThatThrownBy(() -> ctrl.creditAdjust(dto(1L, 500L, null)))
                    .isInstanceOf(BizException.class).hasMessageContaining("操作理由为必填项");
        }

        @Test
        @DisplayName("remark 为全空白 → 必填理由报错")
        void blankRemarkFails() {
            UserContext.set(5L, "admin", UserRole.SUPER_ADMIN.getCode());
            assertThatThrownBy(() -> ctrl.creditAdjust(dto(1L, 500L, "   ")))
                    .isInstanceOf(BizException.class).hasMessageContaining("操作理由为必填项");
        }

        @Test
        @DisplayName("remark 仅1个字 → 至少2字报错")
        void tooShortRemarkFails() {
            UserContext.set(5L, "admin", UserRole.SUPER_ADMIN.getCode());
            assertThatThrownBy(() -> ctrl.creditAdjust(dto(1L, 500L, "补")))
                    .isInstanceOf(BizException.class).hasMessageContaining("至少2个字");
        }

        @Test
        @DisplayName("变动值 10亿以上 → 单次变动值过大")
        void tooLargeThrows() {
            UserContext.set(5L, "admin", UserRole.SUPER_ADMIN.getCode());
            assertThatThrownBy(() -> ctrl.creditAdjust(dto(1L, 1_000_000_001L, "大额")))
                    .isInstanceOf(BizException.class).hasMessageContaining("过大");
        }

        @Test
        @DisplayName("目标用户不存在 → 报错")
        void targetNotExist() {
            UserContext.set(4L, "kefu", UserRole.CUSTOMER_SERVICE.getCode());
            when(userMapper.selectById(99L)).thenReturn(null);
            assertThatThrownBy(() -> ctrl.creditAdjust(dto(99L, 100L, "补发")))
                    .isInstanceOf(BizException.class).hasMessageContaining("目标用户不存在");
        }

        @Test
        @DisplayName("负数扣除 目标余额不够 → 游戏币不足，不调用 service")
        void negativeExceedTargetBalance() {
            UserContext.set(4L, "kefu", UserRole.CUSTOMER_SERVICE.getCode());
            User t = new User(); t.setId(6L); t.setCredits(100L); t.setUsername("p1"); t.setRole(1);
            when(userMapper.selectById(6L)).thenReturn(t);

            assertThatThrownBy(() -> ctrl.creditAdjust(dto(6L, -101L, "超扣")))
                    .isInstanceOf(BizException.class).hasMessageContaining("目标用户游戏币不足");
            verify(creditService, never()).adminAdjust(anyLong(), anyLong(), any());
        }
    }

    @Nested
    @DisplayName("creditAdjust 成功场景")
    class SuccessCases {
        @Test
        @DisplayName("客服加游戏币：前缀=[客服调整](kefu) 变动=+1000 理由=新玩家扶持 → 拼接备注正确，余额返回正确")
        void csIncreasePrefixesRemark() {
            UserContext.set(4L, "kefu", UserRole.CUSTOMER_SERVICE.getCode());
            User t = new User(); t.setId(6L); t.setCredits(0L); t.setUsername("p1"); t.setRole(1);
            when(userMapper.selectById(6L)).thenReturn(t);
            when(creditService.adminAdjust(eq(6L), eq(1000L), any())).thenAnswer(inv -> inv.getArgument(1));

            Long after = ctrl.creditAdjust(dto(6L, 1000L, "新玩家扶持")).getData();
            assertThat(after).isEqualTo(1000L);

            // 验证传给 service 的完整 remark
            org.mockito.ArgumentCaptor<String> capt = org.mockito.ArgumentCaptor.forClass(String.class);
            verify(creditService).adminAdjust(eq(6L), eq(1000L), capt.capture());
            String full = capt.getValue();
            assertThat(full).startsWith("[客服调整](kefu)新玩家扶持");
            assertThat(full).contains("变动=+1000");

            // 成功后触发补扣欠费
            verify(settlementService).repayAgentFeeFailure(6L);
        }

        @Test
        @DisplayName("超管扣游戏币：前缀=[超管调整] 变动=-500")
        void saDecreasePrefixesRemark() {
            UserContext.set(5L, "admin", UserRole.SUPER_ADMIN.getCode());
            User t = new User(); t.setId(2L); t.setCredits(1000L); t.setUsername("daili01"); t.setRole(2);
            when(userMapper.selectById(2L)).thenReturn(t);
            when(creditService.adminAdjust(eq(2L), eq(-500L), any())).thenReturn(500L);

            Long after = ctrl.creditAdjust(dto(2L, -500L, "违规扣除")).getData();
            assertThat(after).isEqualTo(500L);

            org.mockito.ArgumentCaptor<String> capt = org.mockito.ArgumentCaptor.forClass(String.class);
            verify(creditService).adminAdjust(eq(2L), eq(-500L), capt.capture());
            String full = capt.getValue();
            assertThat(full).startsWith("[超管调整](admin)违规扣除");
            assertThat(full).contains("变动=-500");
        }

        @Test
        @DisplayName("负数扣除刚好等于余额 0 = 允许（200 - 200 = 0 不触发不足）")
        void negativeEqualsBalanceOk() {
            UserContext.set(5L, "admin", UserRole.SUPER_ADMIN.getCode());
            User t = new User(); t.setId(6L); t.setCredits(200L); t.setUsername("p1"); t.setRole(1);
            when(userMapper.selectById(6L)).thenReturn(t);
            when(creditService.adminAdjust(eq(6L), eq(-200L), any())).thenReturn(0L);

            Long after = ctrl.creditAdjust(dto(6L, -200L, "清零")).getData();
            assertThat(after).isEqualTo(0L);
        }
    }
}
