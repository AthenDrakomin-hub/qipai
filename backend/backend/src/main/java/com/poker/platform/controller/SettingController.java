package com.poker.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.poker.platform.dto.R;
import com.poker.platform.entity.UserSetting;
import com.poker.platform.mapper.UserSettingMapper;
import com.poker.platform.security.UserContext;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户设置（原型 23-设置页 / 原型 45-牌桌设置面板）
 *
 * 原型 23「设置」页：
 *   音效（开关）、震动（开关）、自动挂机（开关）、背景音乐（开关）、语言「简体中文」
 *   清除缓存「128MB」、检查更新、关于我们「已是最新版本」、版本号「V1.0.0」
 *   底部按钮：退出登录、恢复默认设置
 *
 * 原型 45「游戏设置」面板：
 *   音效、背景音乐、震动、自动挂机、「快速操作 • 显示快捷下注按钮」
 *   底部按钮：取消、退出房间
 */
@RestController
@RequestMapping("/setting")
public class SettingController {

    /** 客户端展示用版本号（原型 23「版本号 V1.0.0」、原型 01「应用版本 1.0.0」） */
    private static final String APP_VERSION = "V1.0.0";

    @Resource
    private UserSettingMapper settingMapper;

    /**
     * 获取用户设置
     * 原型 23 / 45：首次访问自动按默认值初始化
     */
    @GetMapping
    public R<UserSetting> get() {
        return R.ok(loadOrCreate(UserContext.getUserId()));
    }

    /**
     * 更新用户设置（增量更新，只提交需要变更的字段）
     * 原型 23 / 45：各开关切换时调用
     */
    @PutMapping
    public R<UserSetting> update(@RequestBody UserSetting body) {
        Long uid = UserContext.getUserId();
        UserSetting s = loadOrCreate(uid);
        if (body.getSoundEnabled() != null) s.setSoundEnabled(body.getSoundEnabled());
        if (body.getMusicEnabled() != null) s.setMusicEnabled(body.getMusicEnabled());
        if (body.getVibrateEnabled() != null) s.setVibrateEnabled(body.getVibrateEnabled());
        if (body.getAutoPlayEnabled() != null) s.setAutoPlayEnabled(body.getAutoPlayEnabled());
        if (body.getQuickBetEnabled() != null) s.setQuickBetEnabled(body.getQuickBetEnabled());
        if (body.getLanguage() != null && !body.getLanguage().trim().isEmpty()) {
            s.setLanguage(body.getLanguage().trim());
        }
        settingMapper.updateById(s);
        return R.ok(s);
    }

    /**
     * 恢复默认设置
     * 原型 23「恢复默认设置」按钮
     */
    @PostMapping("/reset")
    public R<UserSetting> reset() {
        Long uid = UserContext.getUserId();
        UserSetting s = loadOrCreate(uid);
        s.setSoundEnabled(true);
        s.setMusicEnabled(true);
        s.setVibrateEnabled(false);
        s.setAutoPlayEnabled(false);
        s.setQuickBetEnabled(false);
        s.setLanguage("zh-CN");
        settingMapper.updateById(s);
        return R.ok("已恢复默认设置", s);
    }

    /**
     * 关于我们 / 版本信息
     * 原型 23「检查更新」「关于我们 已是最新版本」「版本号 V1.0.0」
     * 原型 01 启动页「应用版本：1.0.0」
     */
    @GetMapping("/about")
    public R<Map<String, Object>> about() {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("appName", "V-POKER");
        res.put("appSubName", "龙腾竞技平台");
        res.put("version", APP_VERSION);
        res.put("latest", true);
        res.put("updateTip", "已是最新版本");
        res.put("copyright", "抵制不良游戏 拒绝盗版游戏 适度游戏益脑 沉迷游戏伤身 合理安排时间 享受健康生活");
        res.put("icp", "蜀ICP备xxxxxxxx号");
        res.put("agreements", agreements());
        return R.ok(res);
    }

    /**
     * 语言选项（原型 23「语言 简体中文」）
     */
    @GetMapping("/languages")
    public R<List<Map<String, Object>>> languages() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(lang("zh-CN", "简体中文", true));
        list.add(lang("zh-TW", "繁體中文", false));
        list.add(lang("en-US", "English", false));
        return R.ok(list);
    }

    /** 加载或初始化用户设置 */
    private UserSetting loadOrCreate(Long uid) {
        UserSetting s = settingMapper.selectOne(new LambdaQueryWrapper<UserSetting>()
                .eq(UserSetting::getUserId, uid));
        if (s != null) return s;

        s = new UserSetting();
        s.setUserId(uid);
        s.setSoundEnabled(true);
        s.setMusicEnabled(true);
        s.setVibrateEnabled(false);
        s.setAutoPlayEnabled(false);
        s.setQuickBetEnabled(false);
        s.setLanguage("zh-CN");
        settingMapper.insert(s);
        return s;
    }

    private Map<String, Object> lang(String code, String name, boolean current) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("name", name);
        m.put("current", current);
        return m;
    }

    private List<Map<String, Object>> agreements() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(agreement("USER_AGREEMENT", "用户协议"));
        list.add(agreement("PRIVACY_POLICY", "隐私政策"));
        return list;
    }

    private Map<String, Object> agreement(String code, String name) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("name", name);
        return m;
    }
}
