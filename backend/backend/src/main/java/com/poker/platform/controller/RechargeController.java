package com.poker.platform.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.poker.platform.dto.R;
import com.poker.platform.dto.RechargeDTO;
import com.poker.platform.entity.RechargeOrder;
import com.poker.platform.entity.User;
import com.poker.platform.exception.BizException;
import com.poker.platform.mapper.UserMapper;
import com.poker.platform.security.UserContext;
import com.poker.platform.service.WalletService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * 充值（原型 08-充值页）
 *
 * 页面元素映射：
 *  顶部「游戏充值」+ 返回箭头 + 右上角「当前余额 25,860」
 *  充值档位卡片：6元/60万金币、30元/300万金币、68元/680万金币、
 *               128元/1280万金币、328元/3280万金币、648元/6480万金币
 *  自定义金额：输入框 + 后缀「元」
 *  选择支付方式：支付宝 / 微信支付 / 银行卡
 *  按钮「立即充值」
 *  底部「充值即代表同意充值协议」
 */
@RestController
@RequestMapping("/recharge")
public class RechargeController {

    @Resource private WalletService walletService;
    @Resource private UserMapper userMapper;

    /**
     * 充值页初始化数据
     * 原型 08：右上角「当前余额」+ 档位卡片 + 支付方式选项
     */
    @GetMapping("/init")
    public R<Map<String, Object>> init() {
        Long uid = UserContext.getUserId();
        User u = userMapper.selectById(uid);
        if (u == null) throw new BizException("用户不存在");

        Map<String, Object> res = new java.util.LinkedHashMap<>();
        res.put("credits", u.getCredits() == null ? 0L : u.getCredits());
        res.put("packages", walletService.rechargePackages());
        res.put("payMethods", payMethods());
        res.put("rate", WalletService.CREDITS_PER_YUAN);
        res.put("agreementText", "充值即代表同意充值协议");
        return R.ok(res);
    }

    /**
     * 充值档位列表
     * 原型 08 六档卡片（含赠送金币文案）
     */
    @GetMapping("/packages")
    public R<List<Map<String, Object>>> packages() {
        return R.ok(walletService.rechargePackages());
    }

    /** 支持的支付方式（原型 08「选择支付方式」） */
    @GetMapping("/pay-methods")
    public R<List<Map<String, Object>>> payMethodsApi() {
        return R.ok(payMethods());
    }
    /**
     * 创建充值订单
     * 原型 08「立即充值」点击后调用
     */
    @PostMapping("/create")
    public R<RechargeOrder> create(@Validated @RequestBody RechargeDTO dto) {
        return R.ok("下单成功，请完成支付", walletService.createRecharge(UserContext.getUserId(), dto));
    }

    /**
     * 支付（模拟支付回调）
     * 支付成功后游戏币到账，并推送「充值成功」消息（原型 20）
     */
    @PostMapping("/{id}/pay")
    public R<Map<String, Object>> pay(@PathVariable Long id) {
        return R.ok("充值成功", walletService.payRecharge(UserContext.getUserId(), id));
    }

    /**
     * 我的充值记录（分页）
     */
    @GetMapping("/records")
    public R<Page<RechargeOrder>> records(@RequestParam(defaultValue = "1") Integer page,
                                          @RequestParam(defaultValue = "10") Integer size) {
        return R.ok(walletService.myRecharges(UserContext.getUserId(), page, size));
    }

    private List<Map<String, Object>> payMethods() {
        List<Map<String, Object>> list = new java.util.ArrayList<>();
        list.add(method("ALIPAY", "支付宝", "alipay", true));
        list.add(method("WECHAT", "微信支付", "wechat", false));
        list.add(method("BANK", "银行卡", "bank", false));
        return list;
    }

    private Map<String, Object> method(String code, String name, String icon, boolean recommended) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("code", code);
        m.put("name", name);
        m.put("icon", icon);
        m.put("recommended", recommended);
        return m;
    }
}
