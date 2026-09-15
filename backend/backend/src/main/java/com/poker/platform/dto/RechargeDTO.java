package com.poker.platform.dto;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 创建充值订单请求（原型 08-充值页「立即充值」）
 */
public class RechargeDTO implements Serializable {

    /** 充值金额（元），原型档位 6/30/68/128/328/648，也支持自定义 */
    @NotNull(message = "请输入充值金额")
    @DecimalMin(value = "1.00", message = "充值金额不能低于1元")
    private BigDecimal amountYuan;

    /** 支付方式 ALIPAY-支付宝 / WECHAT-微信支付 / BANK-银行卡 */
    @NotNull(message = "请选择支付方式")
    private String payMethod;

    public BigDecimal getAmountYuan() { return amountYuan; }
    public void setAmountYuan(BigDecimal amountYuan) { this.amountYuan = amountYuan; }
    public String getPayMethod() { return payMethod; }
    public void setPayMethod(String payMethod) { this.payMethod = payMethod; }
}
