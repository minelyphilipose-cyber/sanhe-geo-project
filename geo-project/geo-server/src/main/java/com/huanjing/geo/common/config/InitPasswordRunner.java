package com.huanjing.geo.common.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.huanjing.geo.module.system.entity.SysUser;
import com.huanjing.geo.module.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 显式启用时检查 admin 账户，如果密码无法验证则重置为 admin123。
 * 默认禁用，避免应用重启覆盖已设置的账号密码。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "geo.security.init-admin-password", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class InitPasswordRunner implements CommandLineRunner {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        SysUser admin = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, "admin")
        );

        if (admin == null) {
            // Flyway 还没跑完或表不存在，跳过
            log.info("admin 用户不存在，跳过密码初始化");
            return;
        }

        // 检查当前密码是否能匹配 admin123
        if (!passwordEncoder.matches("admin123", admin.getPasswordHash())) {
            String newHash = passwordEncoder.encode("admin123");
            userMapper.update(null,
                    new LambdaUpdateWrapper<SysUser>()
                            .eq(SysUser::getUsername, "admin")
                            .set(SysUser::getPasswordHash, newHash)
            );
            log.info("已重置 admin 密码为 admin123");
        } else {
            log.info("admin 密码验证通过");
        }
    }
}
