package com.poker.platform.controller;

import com.poker.platform.dto.LoginDTO;
import com.poker.platform.dto.LoginVO;
import com.poker.platform.dto.R;
import com.poker.platform.dto.RegisterDTO;
import com.poker.platform.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

/**
 * 认证控制器（无需登录）
 */
@RestController
@RequestMapping("/auth")
@CrossOrigin
public class AuthController {

    @Autowired
    private AuthService authService;

    /**
     * 注册
     */
    @PostMapping("/register")
    public R<LoginVO> register(@Validated @RequestBody RegisterDTO dto, HttpServletRequest request) {
        return R.ok("注册成功", authService.register(dto, request));
    }

    /**
     * 登录
     */
    @PostMapping("/login")
    public R<LoginVO> login(@Validated @RequestBody LoginDTO dto, HttpServletRequest request) {
        return R.ok("登录成功", authService.login(dto, request));
    }

    /**
     * 退出登录（JWT 无状态，服务端仅返回成功，客户端清理本地登录态）
     */
    @PostMapping("/logout")
    public R<Void> logout() {
        return R.ok();
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public R<String> health() {
        return R.ok("ok");
    }
}
