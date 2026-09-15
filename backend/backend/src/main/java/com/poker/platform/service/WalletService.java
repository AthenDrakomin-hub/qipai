package com.poker.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.hutool.core.util.IdUtil;
import com.poker.platform.dto.RechargeDTO;
import com.poker.platform.dto.WithdrawDTO;
import com.poker.platform.entity.RechargeOrder;
import com.poker.platform.entity.User;
import com.poker.platform.entity.WithdrawOrder;
import com.poker.platform.enums.MsgType;
import com.poker.platform.exception.BizException;
import com.poker.platform.mapper.RechargeOrderMapper;
import com.poker.platform.mapper.UserMapper;
import com.poker.platform.mapper.WithdrawOrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 充值 / 提现服务（原型 08-充值页、17-提现页）
 *
 * 换算口径：1 元 = 10,000 游戏币
 *  - 原型 08 档位：6元→60万金币；30元→300万；68元→680万；128元→1280万；328元→3280万；648元→6480万
 *  - 即「某某万金币」= 金额 × 1万，与 1元=10000 游戏币 一致
 */
@Service
public class WalletService {

    private static final Logger log = LoggerFactory.getLogger(WalletService.class);

    /** 1 元兑换游戏币数量 */
    public static final long CREDITS_PER_YUAN = 10_000L;

    /** 最低提现金额（元） */
    public static final BigDecimal WITHDRAW_MIN_YUAN = new BigDecimal("10");

    @Resource private RechargeOrderMapper rechargeMapper;
    @Resource private WithdrawOrderMapper withdrawMapper;
    @Resource private UserMapper userMapper;
    @Resource private CreditService creditService;
    @Resource private MessageService messageService;

    // ==================== 充值（原型 08） ====================

    /**
     * 充值档位列表（原型 08 档位卡片：6元/60万金币、30元/300万金币 …）
     * 返回每档的「金额 + 赠送金币 + 实际到账」
     */
    public List<Map<String, Object>> rechargePackages() {
        BigDecimal[] amounts = {
                new BigDecimal("6"), new BigDecimal("30"), new BigDecimal("68"),
                new BigDecimal("128"), new BigDecimal("328"), new BigDecimal("648")
        };
        List<Map<String, Object>> list = new ArrayList<>();
        for (BigDecimal a : amounts) {
            long credits = a.multiply(BigDecimal.valueOf(CREDITS_PER_YUAN)).longValue();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("amountYuan", a);
            m.put("amountLabel", a.stripTrailingZeros().toPlainString() + "元");
            m.put("giftCredits", credits);
            // 原型展示口径：「60万金币」
            m.put("giftLabel", (credits / 10_000) + "万金币");
            m.put("actualCredits", credits);
            list.add(m);
        }
        return list;
    }

    /**
     * 创建充值订单（原型 08「立即充值」）
     * 说明：当前为无支付网关的模拟下单，创建后需调用 /recharge/{id}/pay 模拟支付回调完成到账；
     *      接入真实支付渠道时，将 payOrder 改由支付平台异步回调触发即可。
     */
    @Transactional(rollbackFor = Exception.class)
    public RechargeOrder createRecharge(Long userId, RechargeDTO dto) {
        User u = userMapper.selectById(userId);
        if (u == null) throw new BizException("用户不存在");
        String method = normalizePayMethod(dto.getPayMethod());
        if (method == null) throw new BizException("不支持的支付方式，可选：ALIPAY / WECHAT / BANK");

        long credits = dto.getAmountYuan().multiply(BigDecimal.valueOf(CREDITS_PER_YUAN)).longValue();

        RechargeOrder o = new RechargeOrder();
        o.setOrderNo("R" + IdUtil.getSnowflakeNextIdStr());
        o.setUserId(userId);
        o.setUsername(u.getUsername());
        o.setAmountYuan(dto.getAmountYuan().setScale(2, RoundingMode.HALF_UP));
        o.setGiftCredits(credits);
        o.setActualCredits(credits);
        o.setPayMethod(method);
        o.setStatus(0);
        rechargeMapper.insert(o);
        log.info("创建充值单 user={} 金额={} 到账={}", u.getUsername(), dto.getAmountYuan(), credits);
        return o;
    }

    /**
     * 支付回调 / 模拟支付完成
     * 支付成功后游戏币入账(type=1 注册赠送口径 → 这里用 type=7 人工调整不贴切，
     * 故使用 type=1「平台赠送」语义的充值入账)，并推送充值成功消息（原型 38 Toast / 原型 20 充值成功消息）
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> payRecharge(Long userId, Long orderId) {
        RechargeOrder o = rechargeMapper.selectById(orderId);
        if (o == null) throw new BizException("订单不存在");
        if (!o.getUserId().equals(userId)) throw new BizException("无权操作该订单");
        if (o.getStatus() == 1) throw new BizException("订单已支付，请勿重复提交");
        if (o.getStatus() != 0) throw new BizException("订单状态异常，无法支付");

        long after = creditService.changeCredits(userId, o.getActualCredits(), 1,
                null, null, null, userId, o.getUsername(),
                "充值到账：" + o.getAmountYuan().stripTrailingZeros().toPlainString() + "元，获得金币 "
                        + o.getActualCredits(), false);

        o.setStatus(1);
        o.setPayTime(LocalDateTime.now());
        rechargeMapper.updateById(o);

        // 原型 20「充值成功」消息
        messageService.send(userId, MsgType.RECHARGE.getCode(), "充值成功",
                "您的" + o.getAmountYuan().stripTrailingZeros().toPlainString() + "元充值已到账，赠送"
                        + (o.getGiftCredits() / 10_000) + "万金币", o.getId());

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("orderNo", o.getOrderNo());
        res.put("status", 1);
        res.put("credits", after);
        res.put("actualCredits", o.getActualCredits());
        return res;
    }

    /** 我的充值记录 */
    public Page<RechargeOrder> myRecharges(Long userId, Integer page, Integer size) {
        return rechargeMapper.selectPage(
                new Page<>(page == null || page < 1 ? 1 : page, size == null || size < 1 ? 10 : Math.min(size, 100)),
                new LambdaQueryWrapper<RechargeOrder>()
                        .eq(RechargeOrder::getUserId, userId)
                        .orderByDesc(RechargeOrder::getId));
    }

    // ==================== 提现（原型 17） ====================

    /** 可提现余额（原型 17「◆ 可提现余额 ◆」） */
    public Map<String, Object> withdrawBalance(Long userId) {
        User u = userMapper.selectById(userId);
        if (u == null) throw new BizException("用户不存在");
        long credits = u.getCredits() == null ? 0L : u.getCredits();
        BigDecimal yuan = BigDecimal.valueOf(credits)
                .divide(BigDecimal.valueOf(CREDITS_PER_YUAN), 2, RoundingMode.DOWN);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("credits", credits);
        res.put("availableYuan", yuan);
        res.put("minYuan", WITHDRAW_MIN_YUAN);
        res.put("rate", CREDITS_PER_YUAN);
        return res;
    }

    /**
     * 提交提现申请（原型 17「提交申请」）
     * 流程：校验余额 → 冻结式扣减游戏币（type=4 提现）→ 生成待处理提现单
     * 说明：游戏币在提交时即扣减，若被驳回由管理端调用 /withdraw/{id}/reject 退回。
     */
    @Transactional(rollbackFor = Exception.class)
    public WithdrawOrder applyWithdraw(Long userId, WithdrawDTO dto) {
        User u = userMapper.selectById(userId);
        if (u == null) throw new BizException("用户不存在");

        BigDecimal amount = dto.getAmountYuan().setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(WITHDRAW_MIN_YUAN) < 0) {
            throw new BizException("单笔提现金额不能低于 " + WITHDRAW_MIN_YUAN.stripTrailingZeros().toPlainString() + " 元");
        }
        String method = normalizePayMethod(dto.getWithdrawMethod());
        if (method == null) throw new BizException("不支持的收款方式，可选：ALIPAY / WECHAT / BANK");
        if (dto.getAccount() == null || dto.getAccount().trim().isEmpty()) {
            throw new BizException("请输入收款账号");
        }

        long needCredits = amount.multiply(BigDecimal.valueOf(CREDITS_PER_YUAN)).longValue();
        long cur = u.getCredits() == null ? 0L : u.getCredits();
        if (cur < needCredits) {
            throw new BizException("可提现余额不足，当前 " + cur + " 游戏币，本次需要 " + needCredits);
        }

        // 扣减游戏币（type=4 提现）
        creditService.changeCredits(userId, -needCredits, 4, null, null, null,
                userId, u.getUsername(),
                "提交提现申请：" + amount.stripTrailingZeros().toPlainString() + "元", false);

        WithdrawOrder o = new WithdrawOrder();
        o.setOrderNo("W" + IdUtil.getSnowflakeNextIdStr());
        o.setUserId(userId);
        o.setUsername(u.getUsername());
        o.setAmountYuan(amount);
        o.setDeductCredits(needCredits);
        o.setWithdrawMethod(method);
        o.setAccount(dto.getAccount().trim());
        o.setStatus(0);
        withdrawMapper.insert(o);
        log.info("提现申请 user={} 金额={} 扣减={}", u.getUsername(), amount, needCredits);
        return o;
    }

    /** 提现记录（原型 17「◆ 提现记录 ◆」：时间、金额、状态） */
    public Page<WithdrawOrder> myWithdraws(Long userId, Integer page, Integer size) {
        return withdrawMapper.selectPage(
                new Page<>(page == null || page < 1 ? 1 : page, size == null || size < 1 ? 10 : Math.min(size, 100)),
                new LambdaQueryWrapper<WithdrawOrder>()
                        .eq(WithdrawOrder::getUserId, userId)
                        .orderByDesc(WithdrawOrder::getId));
    }

    /** 撤销提现申请（仅待处理状态可撤销，游戏币原路退回） */
    @Transactional(rollbackFor = Exception.class)
    public void cancelWithdraw(Long userId, Long orderId) {
        WithdrawOrder o = withdrawMapper.selectById(orderId);
        if (o == null) throw new BizException("提现单不存在");
        if (!o.getUserId().equals(userId)) throw new BizException("无权操作该提现单");
        if (o.getStatus() != 0) throw new BizException("该提现单已处理，无法撤销");

        creditService.changeCredits(userId, o.getDeductCredits(), 4, null, null, null,
                userId, o.getUsername(), "撤销提现申请，退回游戏币：" + o.getDeductCredits(), false);
        withdrawMapper.deleteById(orderId);
    }

    /** 管理端：审核提现（status 1-已到账 2-已驳回），驳回时退回游戏币 */
    @Transactional(rollbackFor = Exception.class)
    public WithdrawOrder auditWithdraw(Long opId, String opName, Long orderId, Integer status, String reason) {
        WithdrawOrder o = withdrawMapper.selectById(orderId);
        if (o == null) throw new BizException("提现单不存在");
        if (o.getStatus() != 0) throw new BizException("该提现单已处理");
        if (status == null || (status != 1 && status != 2)) throw new BizException("审核状态无效，1-已到账 2-已驳回");

        o.setStatus(status);
        o.setAuditId(opId);
        o.setAuditName(opName);
        o.setAuditTime(LocalDateTime.now());
        if (status == 2) {
            o.setFailReason(reason == null ? "审核未通过" : reason);
            creditService.changeCredits(o.getUserId(), o.getDeductCredits(), 4, null, null, null,
                    opId, opName, "提现驳回退回游戏币：" + o.getDeductCredits()
                            + "，原因：" + o.getFailReason(), false);
            messageService.send(o.getUserId(), MsgType.SYSTEM.getCode(), "提现被驳回",
                    "您提交的提现申请（" + o.getAmountYuan() + "元）未通过审核，游戏币已退回账户。原因："
                            + o.getFailReason(), o.getId());
        } else {
            messageService.send(o.getUserId(), MsgType.SYSTEM.getCode(), "提现到账",
                    "您提交的提现申请（" + o.getAmountYuan() + "元）已到账，请查收", o.getId());
        }
        withdrawMapper.updateById(o);
        return o;
    }

    // ==================== 工具 ====================

    /** 归一化支付/收款方式 */
    private String normalizePayMethod(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toUpperCase();
        switch (s) {
            case "ALIPAY": case "支付宝": return "ALIPAY";
            case "WECHAT": case "WECHATPAY": case "微信": case "微信支付": return "WECHAT";
            case "BANK": case "银行卡": return "BANK";
            default: return null;
        }
    }
}
