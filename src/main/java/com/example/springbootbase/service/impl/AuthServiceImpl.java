package com.example.springbootbase.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.springbootbase.auth.Role;
import com.example.springbootbase.dto.request.LoginRequest;
import com.example.springbootbase.dto.request.RegisterRequest;
import com.example.springbootbase.dto.response.LoginResponse;
import com.example.springbootbase.dto.response.RegisterResponse;
import com.example.springbootbase.entity.SessionEntity;
import com.example.springbootbase.entity.TeachingClassEntity;
import com.example.springbootbase.entity.UserEntity;
import com.example.springbootbase.mapper.TeachingClassMapper;
import com.example.springbootbase.mapper.SessionMapper;
import com.example.springbootbase.mapper.UserMapper;
import com.example.springbootbase.model.SessionInfo;
import com.example.springbootbase.model.UserAccount;
import com.example.springbootbase.service.AuthService;
import com.example.springbootbase.util.IdUtil;
import com.example.springbootbase.util.TimeUtil;
import com.example.springbootbase.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

/**
 * 认证服务实现。
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final SessionMapper sessionMapper;
    private final TeachingClassMapper teachingClassMapper;

    @Override
    public RegisterResponse register(RegisterRequest request) {
        validateRegisterRequest(request);

        String username = request.getUsername().trim();
        Role role = Role.fromInput(request.getRole());
        if (findByUsername(username) != null) {
            return RegisterResponse.builder()
                    .success(false)
                    .message("用户名已存在")
                    .build();
        }

        String userId = IdUtil.newId();
        UserEntity userEntity = UserEntity.builder()
                .userId(userId)
                .username(username)
                .password(request.getPassword())
                .role(role.name())
                .classId(role == Role.STUDENT ? 1L : null)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        userMapper.insert(userEntity);
        UserAccount user = toUserAccount(userEntity);

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
        UserEntity userEntity = findByUsername(username);
        UserAccount user = userEntity == null ? null : toUserAccount(userEntity);

        if (user == null || !user.getPassword().equals(request.getPassword()) || user.getRole() != role) {
            return LoginResponse.builder()
                    .success(false)
                    .message("账号、密码或角色不匹配")
                    .build();
        }

        String token = IdUtil.newId();
        SessionInfo sessionInfo = SessionInfo.builder()
                .id(user.getId())
                .token(token)
                .userId(user.getUserId())
                .username(user.getUsername())
                .role(user.getRole())
                .classId(user.getClassId())
                .className(user.getClassName())
                .createdAt(TimeUtil.now())
                .build();

        sessionMapper.insert(SessionEntity.builder()
                .token(token)
                .userId(sessionInfo.getUserId())
                .username(sessionInfo.getUsername())
                .role(sessionInfo.getRole().name())
                .createdAt(OffsetDateTime.now())
                .build());

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
        SessionEntity sessionEntity = sessionMapper.selectById(token);
        if (sessionEntity == null) {
            return null;
        }
        UserEntity userEntity = findByUserId(sessionEntity.getUserId());
        UserAccount user = userEntity == null ? null : toUserAccount(userEntity);
        return SessionInfo.builder()
                .id(user == null ? null : user.getId())
                .token(sessionEntity.getToken())
                .userId(user == null ? sessionEntity.getUserId() : user.getUserId())
                .username(user == null ? sessionEntity.getUsername() : user.getUsername())
                .role(user == null ? Role.fromInput(sessionEntity.getRole()) : user.getRole())
                .classId(user == null ? null : user.getClassId())
                .className(user == null ? null : user.getClassName())
                .createdAt(sessionEntity.getCreatedAt() == null
                        ? TimeUtil.now()
                        : sessionEntity.getCreatedAt().toInstant())
                .build();
    }

    @Override
    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            sessionMapper.deleteById(token);
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
                .id(user.getId())
                .userId(user.getUserId())
                .username(user.getUsername())
                .role(user.getRole())
                .classId(user.getClassId())
                .className(user.getClassName())
                .build();
    }

    private UserEntity findByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getUsername, username)
                .last("LIMIT 1"));
    }

    private UserAccount toUserAccount(UserEntity userEntity) {
        TeachingClassEntity teachingClass = resolveClass(userEntity, Role.fromInput(userEntity.getRole()));
        return UserAccount.builder()
                .id(userEntity.getId())
                .userId(userEntity.getUserId())
                .username(userEntity.getUsername())
                .password(userEntity.getPassword())
                .role(Role.fromInput(userEntity.getRole()))
                .classId(teachingClass == null ? null : teachingClass.getId())
                .className(teachingClass == null ? null : teachingClass.getClassName())
                .createdAt(userEntity.getCreatedAt() == null
                        ? TimeUtil.now()
                        : userEntity.getCreatedAt().toInstant())
                .build();
    }

    private TeachingClassEntity resolveClass(UserEntity userEntity, Role role) {
        if (userEntity == null || role == null) {
            return null;
        }
        if (role == Role.STUDENT) {
            if (userEntity.getClassId() == null) {
                return null;
            }
            return teachingClassMapper.selectById(userEntity.getClassId());
        }
        if (role == Role.TEACHER) {
            return teachingClassMapper.selectOne(new LambdaQueryWrapper<TeachingClassEntity>()
                    .eq(TeachingClassEntity::getTeacherId, userEntity.getId())
                    .orderByAsc(TeachingClassEntity::getId)
                    .last("LIMIT 1"));
        }
        return null;
    }

    private UserEntity findByUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        return userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getUserId, userId)
                .last("LIMIT 1"));
    }
}
