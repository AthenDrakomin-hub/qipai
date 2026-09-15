package com.poker.platform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.poker.platform.dto.R;
import com.poker.platform.dto.WithdrawDTO;
import com.poker.platform.entity.WithdrawOrder;
import com.poker.platform.security.UserContext;
import com.poker.platform.service.WalletService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 提现（原型 17-提现页）
 *
 * 页面元素映射：
 *  顶部「游戏提现」+ 返回箭头 + 右上角「当前余额 25,860」
 *  左卡片「◆ 可提现余额 ◆」金额 + 输入框「请输入提现金额」+ 后缀「元」+ 按钮「全部提现」
 *  右卡片「◆ 选择收款方式 ◆」支付宝 / 微信支付 / 银行卡 +「请输入收款账号」
 *  主按钮「提交申请」
 *  底部「◆ 提现记录 ◆」：时间、金额、状态（已到账）
 */
@RestController
@RequestMapping("/withdraw")
public class WithdrawController {

    @Resource
    private WalletService walletService;

    /**
     * 提现页初始化
     * 原型 17：可提现余额 + 收款方式选项
     */
    @GetMapping("/init")
    public R<Map<String, Object>> init() {
        Map<String, Object> res = walletService.withdrawBalance(UserContext.getUserId());
        res.put("withdrawMethods", methods());
        return R.ok(res);
    }

    /**
     * 可提现余额
     * 原型 17「◆ 可提现余额 ◆」25,860
     */
    @GetMapping("/balance")
    public R<Map<String, Object>> balance() {
        return R.ok(walletService.withdrawBalance(UserContext.getUserId()));
    }

    /** 支持的收款方式（原型 17「◆ 选择收款方式 ◆」） */
    @GetMapping("/withdraw-methods")
    public R<List<Map<String, Object>>> withdrawMethods() {
        return R.ok(methods());
    }

    /**
     * 提交提现申请
     * 原型 17「提交申请」
     */
    @PostMapping("/apply")
    public R<WithdrawOrder> apply(@Validated @RequestBody WithdrawDTO dto) {
        return R.ok("提现申请已提交，请等待审核", walletService.applyWithdraw(UserContext.getUserId(), dto));
    }

    /**
     * 提现记录（分页）
     * 原型 17「◆ 提现记录 ◆」：2026-09-10 14:30 / 提现 5,000 / 已到账
     */
    @GetMapping("/records")
    public R<Page<WithdrawOrder>> records(@RequestParam(defaultValue = "1") Integer page,
                                          @RequestParam(defaultValue = "10") Integer size) {
        return R.ok(walletService.myWithdraws(UserContext.getUserId(), page, size));
    }

    /**
     * 撤销提现申请（仅「待处理」可撤销，游戏币原路退回）
     */
    @PostMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id) {
        walletService.cancelWithdraw(UserContext.getUserId(), id);
        return R.ok("已撤销提现申请，游戏币已退回", null);
    }

    private List<Map<String, Object>> methods() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(method("ALIPAY", "支付宝", "alipay", false));
        list.add(method("WECHAT", "微信支付", "wechat", false));
        list.add(method("BANK", "银行卡", "bank", true));
        return list;
    }

    private Map<String, Object> method(String code, String name, String icon, boolean recommended) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("name", name);
        m.put("icon", icon);
        m.put("recommended", recommended);
        return m;
    }
}
