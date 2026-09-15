package com.poker.platform.dto;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 提交提现申请请求（原型 17-提现页「提交申请」）
 */
public class WithdrawDTO implements Serializable {

    /** 提现金额（元），原型「请输入提现金额」 */
    @NotNull(message = "请输入提现金额")
    @DecimalMin(value = "1.00", message = "提现金额不能低于1元")
    private BigDecimal amountYuan;

    /** 收款方式 ALIPAY-支付宝 / WECHAT-微信支付 / BANK-银行卡 */
    @NotNull(message = "请选择收款方式")
    private String withdrawMethod;

    /** 收款账号，原型「请输入收款账号」 */
    @NotBlank(message = "请输入收款账号")
    private String account;

    public BigDecimal getAmountYuan() { return amountYuan; }
    public void setAmountYuan(BigDecimal amountYuan) { this.amountYuan = amountYuan; }
    public String getWithdrawMethod() { return withdrawMethod; }
    public void setWithdrawMethod(String withdrawMethod) { this.withdrawMethod = withdrawMethod; }
    public String getAccount() { return account; }
    public void setAccount(String account) { this.account = account; }
}
