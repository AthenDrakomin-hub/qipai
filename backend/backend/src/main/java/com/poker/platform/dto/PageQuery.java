package com.poker.platform.dto;

import java.io.Serializable;

/**
 * 分页查询通用请求（原型各列表页 Tab 筛选 + 分页）
 */
public class PageQuery implements Serializable {

    /** 页码，从 1 开始 */
    private Integer page = 1;

    /** 每页条数 */
    private Integer size = 10;

    /** 筛选 Tab（含义由各接口自行定义：全部传空） */
    private String tab;

    /** 业务类型筛选（如 gameType / changeType / msgType） */
    private Integer type;

    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }
    public Integer getSize() { return size; }
    public void setSize(Integer size) { this.size = size; }
    public String getTab() { return tab; }
    public void setTab(String tab) { this.tab = tab; }
    public Integer getType() { return type; }
    public void setType(Integer type) { this.type = type; }

    /** 安全页码（防止前端传 0 或负数） */
    public int safePage() {
        return page == null || page < 1 ? 1 : page;
    }

    /** 安全页大小（1-100） */
    public int safeSize() {
        if (size == null || size < 1) return 10;
        return Math.min(size, 100);
    }
}
