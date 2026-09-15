package com.poker.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.poker.platform.entity.User;
import com.poker.platform.enums.UserRole;
import com.poker.platform.exception.BizException;
import com.poker.platform.mapper.CreditLogMapper;
import com.poker.platform.mapper.RakeRebateLogMapper;
import com.poker.platform.mapper.UserMapper;
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
@DisplayName("UserController 游戏币接口 单测：agentAdjust / playerToPlayer / promoteGrant")
class UserControllerTransferTest {

    @Mock private UserMapper userMapper;
    @Mock private CreditLogMapper creditLogMapper;
    @Mock private RakeRebateLogMapper rakeRebateLogMapper;
    @Mock private CreditService creditService;
    @Mock private SettlementService settlementService;
    @InjectMocks private UserController ctrl;

    @BeforeEach
    void setUp() { UserContext.clear(); }
    @AfterEach
    void tearDown() { UserContext.clear(); }

    static UserController.AdjustDTO adj(Long uid, Long cv, String remark) {
        UserController.AdjustDTO d = new UserController.AdjustDTO();
        d.targetUserId = uid; d.changeValue = cv; d.remark = remark; return d;
    }
    static UserController.TransferDTO trans(String to, Long v, String r) {
        UserController.TransferDTO d = new UserController.TransferDTO();
        d.toUsername = to; d.value = v; d.remark = r; return d;
    }
    static User user(Long id, String name, int role, long credits, Long parentId, Integer status) {
        User u = new User(); u.setId(id); u.setUsername(name); u.setNickname(name+"_n");
        u.setRole(role); u.setCredits(credits); u.setParentId(parentId); u.setStatus(status);
        return u;
    }

    // =========================================================================
    // agentAdjust
    // =========================================================================
    @Nested
    @DisplayName("agentAdjust 游戏币增减 接口")
    class AgentAdjust {

        @Test
        @DisplayName("客服虚拟出库：理由必填校验通过，变动+2000，目标=总代理 OK")
        void csGrantToGeneralAgentVirtualOutbound() {
            UserContext.set(4L, "kefu", UserRole.CUSTOMER_SERVICE.getCode());
            User target = user(3L, "zongdai", UserRole.GENERAL_AGENT.getCode(), 100L, 5L, 0);
            when(userMapper.selectById(3L)).thenReturn(target);
            when(creditService.adminAdjust(eq(3L), eq(2000L), any())).thenReturn(2100L);

            Long after = ctrl.agentAdjust(adj(3L, 2000L, "客服月度调拨")).getData();
            assertThat(after).isEqualTo(2100L);

            org.mockito.ArgumentCaptor<String> capt = org.mockito.ArgumentCaptor.forClass(String.class);
            verify(creditService).adminAdjust(eq(3L), eq(2000L), capt.capture());
            String remark = capt.getValue();
            assertThat(remark).startsWith("[客服调整](kefu)客服月度调拨");
            assertThat(remark).contains("变动=+2000");

            // 调用 repayAgentFeeFailure（虚拟出库时也会）
            verify(settlementService).repayAgentFeeFailure(3L);
        }

        @Test
        @DisplayName("超管：理由=单字 → 至少2字 报错")
        void saShortRemarkFails() {
            UserContext.set(5L, "admin", UserRole.SUPER_ADMIN.getCode());
            User t = user(6L, "p1", 1, 0L, 2L, 0);
            when(userMapper.selectById(6L)).thenReturn(t);
            assertThatThrownBy(() -> ctrl.agentAdjust(adj(6L, 100L, "x")))
                    .isInstanceOf(BizException.class).hasMessageContaining("至少2个字");
        }

        @Test
        @DisplayName("客服减 目标余额=50 想扣-60 → 目标游戏币不足")
        void csDecreaseExceedTargetFails() {
            UserContext.set(4L, "kefu", UserRole.CUSTOMER_SERVICE.getCode());
            User t = user(6L, "p1", 1, 50L, 2L, 0);
            when(userMapper.selectById(6L)).thenReturn(t);
            assertThatThrownBy(() -> ctrl.agentAdjust(adj(6L, -60L, "扣超")))
                    .isInstanceOf(BizException.class).hasMessageContaining("目标用户游戏币不足");
        }

        @Test
        @DisplayName("二级代理 给自己下级玩家加500：代理余额2000，先扣代理，再加玩家，最终玩家余额=500")
        void agentAddSubordinateDeductsAgentCredits() {
            UserContext.set(2L, "daili01", UserRole.AGENT.getCode());
            User me = user(2L, "daili01", UserRole.AGENT.getCode(), 2000L, 3L, 0);
            User sub = user(6L, "p1", UserRole.PLAYER.getCode(), 0L, 2L, 0);

            when(userMapper.selectById(6L)).thenReturn(sub);
            when(userMapper.selectById(2L)).thenReturn(me);
            when(creditService.agentAdjustSubordinate(eq(2L), eq("daili01"), eq(6L), eq(500L), eq("周奖")))
                    .thenReturn(500L);

            Long after = ctrl.agentAdjust(adj(6L, 500L, "周奖")).getData();
            assertThat(after).isEqualTo(500L);

            // 先从代理扣 500（changeCredits(uid, -500, 2, ...)）
            verify(creditService).changeCredits(eq(2L), eq(-500L), eq(2), any(), any(), any(),
                    eq(2L), eq("daili01"), org.mockito.ArgumentMatchers.contains("周奖"), eq(false));
        }

        @Test
        @DisplayName("二级代理加下级但自身不够 → 失败，玩家不获得")
        void agentAddSubordinateNotEnough() {
            UserContext.set(2L, "daili01", UserRole.AGENT.getCode());
            User sub = user(6L, "p1", UserRole.PLAYER.getCode(), 0L, 2L, 0);
            User me = user(2L, "daili01", UserRole.AGENT.getCode(), 100L, 3L, 0);

            when(userMapper.selectById(6L)).thenReturn(sub);
            when(userMapper.selectById(2L)).thenReturn(me);

            assertThatThrownBy(() -> ctrl.agentAdjust(adj(6L, 500L, "奖")))
                    .isInstanceOf(BizException.class).hasMessageContaining("您的游戏币不足");
            verify(creditService, never()).agentAdjustSubordinate(anyLong(), any(), anyLong(), anyLong(), any());
        }

        @Test
        @DisplayName("二级代理扣下级 超过下级余额 → 失败")
        void agentDeductSubExceedFails() {
            UserContext.set(2L, "daili01", UserRole.AGENT.getCode());
            User sub = user(6L, "p1", 1, 50L, 2L, 0);
            when(userMapper.selectById(6L)).thenReturn(sub);

            assertThatThrownBy(() -> ctrl.agentAdjust(adj(6L, -51L, "罚")))
                    .isInstanceOf(BizException.class).hasMessageContaining("下级游戏币不足");
        }

        @Test
        @DisplayName("二级代理加非下级玩家 → 目标用户不是您的下级玩家")
        void agentAddNonSubFails() {
            UserContext.set(2L, "daili01", UserRole.AGENT.getCode());
            User other = user(99L, "p99", 1, 100L, 999L, 0);
            when(userMapper.selectById(99L)).thenReturn(other);

            assertThatThrownBy(() -> ctrl.agentAdjust(adj(99L, 500L, "奖")))
                    .isInstanceOf(BizException.class).hasMessageContaining("不是您的直接下级");
        }

        @Test
        @DisplayName("玩家 role=1 调用 agentAdjust → 仅二级代理/客服/超管可操作")
        void playerAgentAdjustFails() {
            UserContext.set(1L, "p1", UserRole.PLAYER.getCode());
            User t = user(2L, "p2", 1, 0L, 2L, 0);
            when(userMapper.selectById(2L)).thenReturn(t);
            assertThatThrownBy(() -> ctrl.agentAdjust(adj(2L, 100L, "x")))
                    .isInstanceOf(BizException.class).hasMessageContaining("仅代理/客服/超管可操作");
        }
    }

    // =========================================================================
    // playerToPlayer P2P
    // =========================================================================
    @Nested
    @DisplayName("playerToPlayer P2P 玩家赠送")
    class PlayerToPlayer {

        @Test
        @DisplayName("非玩家角色：总代理 role=3 → 仅玩家可使用，报错")
        void generalAgentCannotUse() {
            UserContext.set(3L, "zongdai", UserRole.GENERAL_AGENT.getCode());
            assertThatThrownBy(() -> ctrl.playerToPlayer(trans("p1", 500L, "送")))
                    .isInstanceOf(BizException.class).hasMessageContaining("仅玩家可使用");
        }

        @Test
        @DisplayName("玩家送给自己 → 不能向自己赠送")
        void selfSend() {
            UserContext.set(1L, "p1", UserRole.PLAYER.getCode());
            assertThatThrownBy(() -> ctrl.playerToPlayer(trans("p1", 500L, "x")))
                    .isInstanceOf(BizException.class).hasMessageContaining("不能向自己赠送");
        }

        @Test
        @DisplayName("目标 username 不存在 → 接收方账号不存在")
        void toUserNotFound() {
            UserContext.set(1L, "p1", UserRole.PLAYER.getCode());
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            assertThatThrownBy(() -> ctrl.playerToPlayer(trans("unknown", 500L, "x")))
                    .isInstanceOf(BizException.class).hasMessageContaining("接收方账号不存在");
        }

        @Test
        @DisplayName("目标是二级代理 → P2P 仅支持玩家→玩家")
        void targetRoleNotPlayer() {
            UserContext.set(1L, "p1", UserRole.PLAYER.getCode());
            User ag = user(2L, "daili01", UserRole.AGENT.getCode(), 0L, 3L, 0);
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(ag);
            assertThatThrownBy(() -> ctrl.playerToPlayer(trans("daili01", 500L, "x")))
                    .isInstanceOf(BizException.class).hasMessageContaining("P2P赠送仅支持玩家→玩家");
        }

        @Test
        @DisplayName("接收方被冻结 status=1 → 账号已被冻结，拒绝")
        void targetFrozen() {
            UserContext.set(1L, "p1", UserRole.PLAYER.getCode());
            User p2 = user(2L, "p2", UserRole.PLAYER.getCode(), 100L, 3L, 1);
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(p2);
            assertThatThrownBy(() -> ctrl.playerToPlayer(trans("p2", 50L, "x")))
                    .isInstanceOf(BizException.class).hasMessageContaining("已被冻结");
        }

        @Test
        @DisplayName("value=0 或 null 抛异常")
        void zeroValueFails() {
            UserContext.set(1L, "p1", UserRole.PLAYER.getCode());
            assertThatThrownBy(() -> ctrl.playerToPlayer(trans("p2", 0L, "x")))
                    .isInstanceOf(BizException.class).hasMessageContaining("必须大于0");
        }

        @Test
        @DisplayName("Happy path：玩家p1→p2 500，返回 creditService.playerTransfer 返回值")
        void happyPath() {
            UserContext.set(1L, "p1", UserRole.PLAYER.getCode());
            User p2 = user(2L, "p2", UserRole.PLAYER.getCode(), 0L, 3L, 0);
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(p2);
            when(creditService.playerTransfer(eq(1L), eq("p1"), eq(2L), eq("p2"), eq(500L), eq("晚餐")))
                    .thenReturn(500L);

            Long after = ctrl.playerToPlayer(trans("p2", 500L, "晚餐")).getData();
            assertThat(after).isEqualTo(500L);
        }
    }

    // =========================================================================
    // promoteGrant 层级划拨
    // =========================================================================
    @Nested
    @DisplayName("promoteGrant 层级划拨（推广发放游戏币）")
    class PromoteGrant {

        @Test
        @DisplayName("二级代理 → 下级玩家：deductFrom=true；非下级报错")
        void agentToNotSubFails() {
            UserContext.set(2L, "daili01", UserRole.AGENT.getCode());
            User p = user(99L, "pX", UserRole.PLAYER.getCode(), 0L, 999L, 0);
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(p);
            assertThatThrownBy(() -> ctrl.promoteGrant(trans("pX", 500L, "x")))
                    .isInstanceOf(BizException.class).hasMessageContaining("不是您的下级玩家");
        }

        @Test
        @DisplayName("总代理 → 下级玩家(非二级代理)：总代理仅能向下级二级代理划拨 报错")
        void gaToPlayerFails() {
            UserContext.set(3L, "zongdai", UserRole.GENERAL_AGENT.getCode());
            User p = user(6L, "p1", UserRole.PLAYER.getCode(), 0L, 2L, 0);
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(p);
            assertThatThrownBy(() -> ctrl.promoteGrant(trans("p1", 1000L, "x")))
                    .isInstanceOf(BizException.class).hasMessageContaining("总代理仅能向下级一级代理");
        }

        @Test
        @DisplayName("总代理 → 自己的一级代理：成功 deductFrom=true")
        void gaToPrimaryAgent() {
            UserContext.set(3L, "zongdai", UserRole.GENERAL_AGENT.getCode());
            User ag = user(6L, "hexindaili", UserRole.PRIMARY_AGENT.getCode(), 500L, 3L, 0);
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(ag);
            when(creditService.promoteGrant(eq(3L), eq("zongdai"), eq(6L), eq("hexindaili"),
                    eq(UserRole.GENERAL_AGENT.getCode()), eq(1000L), eq("月度"), eq(true)))
                    .thenReturn(1500L);

            Long after = ctrl.promoteGrant(trans("hexindaili", 1000L, "月度")).getData();
            assertThat(after).isEqualTo(1500L);
        }

        @Test
        @DisplayName("客服 → 总代理：虚拟出库 deductFrom=false，成功")
        void csToGeneralAgent() {
            UserContext.set(4L, "kefu", UserRole.CUSTOMER_SERVICE.getCode());
            User ga = user(3L, "zongdai", UserRole.GENERAL_AGENT.getCode(), 100L, 5L, 0);
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(ga);
            when(creditService.promoteGrant(eq(4L), eq("kefu"), eq(3L), eq("zongdai"),
                    eq(UserRole.CUSTOMER_SERVICE.getCode()), eq(5000L), eq("调拨"), eq(false)))
                    .thenReturn(5100L);
            Long after = ctrl.promoteGrant(trans("zongdai", 5000L, "调拨")).getData();
            assertThat(after).isEqualTo(5100L);
        }

        @Test
        @DisplayName("客服 → 另一个客服：客服不能向客服/超管划拨，报错")
        void csToCsFails() {
            UserContext.set(4L, "kefu", UserRole.CUSTOMER_SERVICE.getCode());
            User cs = user(41L, "kefu2", UserRole.CUSTOMER_SERVICE.getCode(), 0L, 5L, 0);
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(cs);
            assertThatThrownBy(() -> ctrl.promoteGrant(trans("kefu2", 1000L, "x")))
                    .isInstanceOf(BizException.class).hasMessageContaining("不能向客服或超管");
        }

        @Test
        @DisplayName("客服 → 超管：同上报错")
        void csToSuperAdminFails() {
            UserContext.set(4L, "kefu", UserRole.CUSTOMER_SERVICE.getCode());
            User sa = user(5L, "admin", UserRole.SUPER_ADMIN.getCode(), 0L, null, 0);
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(sa);
            assertThatThrownBy(() -> ctrl.promoteGrant(trans("admin", 1000L, "x")))
                    .isInstanceOf(BizException.class).hasMessageContaining("不能向客服或超管");
        }

        @Test
        @DisplayName("玩家 role=1 调用 promoteGrant → 您没有层级划拨权限")
        void playerNotAuthorized() {
            UserContext.set(1L, "p1", UserRole.PLAYER.getCode());
            User p = user(2L, "p2", UserRole.PLAYER.getCode(), 0L, 3L, 0);
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(p);
            assertThatThrownBy(() -> ctrl.promoteGrant(trans("p2", 100L, "x")))
                    .isInstanceOf(BizException.class).hasMessageContaining("没有层级划拨权限");
        }
    }
}
