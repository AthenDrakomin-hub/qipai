package com.poker.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.poker.platform.dto.LoginDTO;
import com.poker.platform.dto.LoginVO;
import com.poker.platform.dto.RegisterDTO;
import com.poker.platform.entity.CreditLog;
import com.poker.platform.entity.User;
import com.poker.platform.enums.UserRole;
import com.poker.platform.exception.BizException;
import com.poker.platform.mapper.CreditLogMapper;
import com.poker.platform.mapper.UserMapper;
import com.poker.platform.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;

/**
 * 认证服务
 */
@Service
public class AuthService extends ServiceImpl<UserMapper, User> {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private CreditLogMapper creditLogMapper;
    @Autowired
    private JwtUtil jwtUtil;

    /**
     * 注册
     * 规则：邀请码匹配 -> 自动归属为邀请码用户的下级（四级体系：总代→一级→二级→玩家）
     * - 邀请码来自超管 -> 注册为总代理
     * - 邀请码来自总代理 -> 注册为一级代理（核心代理）
     * - 邀请码来自一级代理 -> 注册为二级代理（高级代理）
     * - 邀请码来自二级代理 -> 注册为普通玩家
     * - 邀请码来自玩家 -> 不允许（玩家不可发展下线）
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginVO register(RegisterDTO dto, HttpServletRequest request) {
        // 1. 基础校验
        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            throw new BizException("两次密码输入不一致");
        }
        if (dto.getSecurityCode() == null || dto.getSecurityCode().trim().isEmpty()) {
            throw new BizException("安全码不能为空");
        }
        if (dto.getSecurityCode().length() < 4 || dto.getSecurityCode().length() > 20) {
            throw new BizException("安全码长度需为 4-20 位");
        }
        if (dto.getSecurityCode().equals(dto.getPassword())) {
            throw new BizException("安全码不能与登录密码相同");
        }

        // 2. 账号唯一性
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, dto.getUsername()));
        if (exists != null && exists > 0) {
            throw new BizException("账号已存在");
        }

        // 3. 校验邀请码有效性
        User parent = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getInviteCode, dto.getInviteCode().toUpperCase()));
        if (parent == null) {
            throw new BizException("邀请码无效");
        }

        UserRole parentRole = UserRole.fromCode(parent.getRole());
        Integer newUserRole;
        long initCredits = 0;
        String initRemark = "";

        if (parentRole == UserRole.SUPER_ADMIN) {
            // 超级管理员邀请码 -> 注册为总代理
            newUserRole = UserRole.GENERAL_AGENT.getCode();
            initCredits = 0;
            initRemark = "超管[" + parent.getUsername() + "]邀请注册为总代理";
        } else if (parentRole == UserRole.GENERAL_AGENT) {
            // 总代理邀请码 -> 注册为一级代理（核心代理）
            newUserRole = UserRole.PRIMARY_AGENT.getCode();
            initCredits = 0;
            initRemark = "总代理[" + parent.getUsername() + "]邀请注册为一级代理";
        } else if (parentRole == UserRole.PRIMARY_AGENT) {
            // 一级代理邀请码 -> 注册为二级代理（高级代理）
            newUserRole = UserRole.AGENT.getCode();
            initCredits = 0;
            initRemark = "一级代理[" + parent.getUsername() + "]邀请注册为二级代理";
        } else if (parentRole == UserRole.AGENT) {
            // 二级代理邀请码 -> 注册为普通玩家
            newUserRole = UserRole.PLAYER.getCode();
            initCredits = 0;
            initRemark = "二级代理[" + parent.getUsername() + "]邀请注册为玩家";
        } else if (parentRole == UserRole.CUSTOMER_SERVICE) {
            // 客服邀请 -> 默认注册为玩家
            newUserRole = UserRole.PLAYER.getCode();
            initRemark = "客服邀请注册";
        } else {
            throw new BizException("该邀请码用户无权邀请下线，请使用超管/总代/一级代理/二级代理邀请码");
        }

        // 4. 生成6位唯一邀请码
        String inviteCode;
        int retry = 0;
        do {
            inviteCode = JwtUtil.generateInviteCode();
            Long c = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getInviteCode, inviteCode));
            if (c == null || c == 0) break;
            retry++;
        } while (retry < 20);

        // 5. 创建用户
        User user = new User();
        user.setUsername(dto.getUsername());
        String salt = JwtUtil.generateSalt();
        user.setSalt(salt);
        user.setPassword(JwtUtil.encryptPassword(dto.getPassword(), salt));
        // 安全码独立存储（加密），不再回退为登录密码
        user.setSecurityCode(JwtUtil.encryptPassword(dto.getSecurityCode(), salt));
        user.setNickname(dto.getNickname() != null && !dto.getNickname().isEmpty() ? dto.getNickname() : dto.getUsername());
        user.setRole(newUserRole);
        user.setCredits(initCredits);
        user.setInviteCode(inviteCode);
        user.setParentId(parent.getId());
        user.setParentInviteCode(parent.getInviteCode());
        user.setHasFeeFailure(false);
        user.setStatus(0);
        user.setLastLoginIp(getClientIp(request));
        user.setLastLoginTime(LocalDateTime.now());
        userMapper.insert(user);

        // 6. 积分流水记录
        if (initCredits > 0) {
            CreditLog cl = new CreditLog();
            cl.setUserId(user.getId());
            cl.setUsername(user.getUsername());
            cl.setChangeType(1);
            cl.setChangeValue(initCredits);
            cl.setBeforeValue(0L);
            cl.setAfterValue(initCredits);
            cl.setRemark(initRemark);
            creditLogMapper.insert(cl);
        }

        log.info("注册成功: {} 角色={} 上级={}", user.getUsername(), newUserRole, parent.getUsername());
        return buildLoginVO(user);
    }

    /**
     * 登录
     */
    public LoginVO login(LoginDTO dto, HttpServletRequest request) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername()));
        if (user == null) {
            throw new BizException("账号或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 1) {
            throw new BizException("账号已被禁用");
        }
        String expected = JwtUtil.encryptPassword(dto.getPassword(), user.getSalt());
        if (!expected.equals(user.getPassword())) {
            throw new BizException("账号或密码错误");
        }

        user.setLastLoginIp(getClientIp(request));
        user.setLastLoginTime(LocalDateTime.now());
        userMapper.updateById(user);

        log.info("登录成功: {} role={}", user.getUsername(), user.getRole());
        return buildLoginVO(user);
    }

    private LoginVO buildLoginVO(User user) {
        String token = jwtUtil.createToken(user.getId(), user.getUsername(), user.getRole());
        UserRole role = UserRole.fromCode(user.getRole());
        return new LoginVO(
                token,
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getRole(),
                role == null ? "" : role.getDesc(),
                user.getCredits() == null ? 0L : user.getCredits(),
                user.getInviteCode(),
                user.getParentInviteCode(),
                user.getHasFeeFailure() != null && user.getHasFeeFailure(),
                user.getAvatar()
        );
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) ip = ip.split(",")[0].trim();
        return ip;
    }
}
