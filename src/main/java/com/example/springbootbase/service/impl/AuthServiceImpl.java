package com.example.springbootbase.service.impl;

import com.example.springbootbase.auth.Role;
import com.example.springbootbase.dto.request.LoginRequest;
import com.example.springbootbase.dto.request.RegisterRequest;
import com.example.springbootbase.dto.response.LoginResponse;
import com.example.springbootbase.dto.response.RegisterResponse;
import com.example.springbootbase.model.SessionInfo;
import com.example.springbootbase.model.UserAccount;
import com.example.springbootbase.service.AuthService;
import com.example.springbootbase.store.InMemoryDataStore;
import com.example.springbootbase.util.IdUtil;
import com.example.springbootbase.util.TimeUtil;
import com.example.springbootbase.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 认证服务实现。
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final InMemoryDataStore store;

    @Override
    public RegisterResponse register(RegisterRequest request) {
        validateRegisterRequest(request);

        String username = request.getUsername().trim();
        Role role = Role.fromInput(request.getRole());
        if (store.getUsersByUsername().containsKey(username)) {
            return RegisterResponse.builder()
                    .success(false)
                    .message("用户名已存在")
                    .build();
        }

        UserAccount user = UserAccount.builder()
                .userId(IdUtil.newId())
                .username(username)
                .password(request.getPassword())
                .role(role)
                .createdAt(TimeUtil.now())
                .build();

        store.getUsersByUsername().put(username, user);

        return RegisterResponse.builder()
                .success(true)
                .message("注册成功")
                .user(toUserVO(user))
                .build();
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        validateLoginRequest(request);

        String username = request.getUsername().trim();
        Role role = Role.fromInput(request.getRole());
        UserAccount user = store.getUsersByUsername().get(username);

        if (user == null || !user.getPassword().equals(request.getPassword()) || user.getRole() != role) {
            return LoginResponse.builder()
                    .success(false)
                    .message("账号、密码或角色不匹配")
                    .build();
        }

        String token = IdUtil.newId();
        SessionInfo sessionInfo = SessionInfo.builder()
                .token(token)
                .userId(user.getUserId())
                .username(user.getUsername())
                .role(user.getRole())
                .createdAt(TimeUtil.now())
                .build();

        store.getSessionsByToken().put(token, sessionInfo);

        return LoginResponse.builder()
                .success(true)
                .message("登录成功")
                .token(token)
                .user(toUserVO(user))
                .build();
    }

    @Override
    public SessionInfo getSessionByToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return store.getSessionsByToken().get(token);
    }

    @Override
    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            store.getSessionsByToken().remove(token);
        }
    }

    private void validateRegisterRequest(RegisterRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (request.getPassword() == null || request.getPassword().length() < 6) {
            throw new IllegalArgumentException("密码长度不能少于 6 位");
        }
        if (request.getRole() == null || request.getRole().isBlank()) {
            throw new IllegalArgumentException("角色不能为空");
        }
    }

    private void validateLoginRequest(LoginRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        if (request.getRole() == null || request.getRole().isBlank()) {
            throw new IllegalArgumentException("角色不能为空");
        }
    }

    private UserVO toUserVO(UserAccount user) {
        return UserVO.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .role(user.getRole())
                .build();
    }
}
