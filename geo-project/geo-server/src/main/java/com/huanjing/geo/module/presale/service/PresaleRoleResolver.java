package com.huanjing.geo.module.presale.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 集中管理 presale 模块的角色判定。
 *
 * <p>配置驱动(Step 5 团队定稿):</p>
 * <ul>
 *   <li>{@code presale.security.role-override=false} - 生产必须 false;dev/demo 可 true</li>
 *   <li>{@code presale.security.role-override-target=manager} - override 时把所有用户当什么角色</li>
 * </ul>
 *
 * <p>即使 override=true,高风险操作(删除报告 / 解冻 / 编辑规则库)仍然必须 manager,
 * 这里的 override 只是保证 dev/demo 全员都能被判为 manager,方便演示。</p>
 *
 * <p><b>安全契约(Codex P1-5 修复):</b></p>
 * <ul>
 *   <li>生产环境(role-override=false)必须走真实 {@code sys_user_role} 查询</li>
 *   <li>真实查询在 P1·F·1·b 接入 sys 模块前,{@link #currentRole()} 会抛出
 *       {@link IllegalStateException} 强制报错,避免"生产环境无声开放权限"</li>
 *   <li>这个 fail-safe 默认替换了旧版 "return Role.MANAGER" 的反安全实现</li>
 * </ul>
 *
 * <p>P1·F·1·a 可用模式:</p>
 * <ol>
 *   <li><b>dev/demo</b>:配置 role-override=true + role-override-target=manager 即可使用</li>
 *   <li><b>prod</b>:必须等 sys 模块接入后才能启用(启用前此类抛异常)</li>
 * </ol>
 */
@Component
public class PresaleRoleResolver {

    private static final Logger log = LoggerFactory.getLogger(PresaleRoleResolver.class);

    public enum Role {
        VIEWER, SALES, MANAGER
    }

    @Value("${presale.security.role-override:false}")
    private boolean roleOverride;

    @Value("${presale.security.role-override-target:manager}")
    private String roleOverrideTarget;

    /**
     * 返回当前用户在 presale 模块的角色。
     *
     * <p>策略:</p>
     * <ol>
     *   <li>role-override=true → 按 role-override-target 返回</li>
     *   <li>role-override=false → 真实角色解析(sys 模块),P1·F·1·b 前尚未接入,
     *       此处抛 {@link IllegalStateException} 保证安全</li>
     * </ol>
     *
     * @throws IllegalStateException 如果 override=false 且真实角色解析尚未接入
     */
    public Role currentRole() {
        if (roleOverride) {
            try {
                return Role.valueOf(roleOverrideTarget.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid role-override-target='{}', falling back to VIEWER",
                        roleOverrideTarget);
                return Role.VIEWER;
            }
        }
        // fail-safe: 生产环境 override=false 但 sys 集成未接入 → 拒绝服务
        // 这是一个明确的"必须在 P1·F·1·b 接入真实角色"的运行时保障
        throw new IllegalStateException(
                "Real role resolution is not yet wired up. " +
                "Set presale.security.role-override=true for dev/demo, " +
                "or wait until sys_user_role integration lands in P1·F·1·b. " +
                "This exception is intentional to prevent silent permission grants in production."
        );
    }

    public boolean canCreate() {
        Role r = currentRole();
        return r == Role.SALES || r == Role.MANAGER;
    }

    public boolean canEdit() {
        Role r = currentRole();
        return r == Role.SALES || r == Role.MANAGER;
    }

    /** 高风险操作:删除版本/报告,解冻,规则库编辑。 */
    public boolean canHighRiskOperate() {
        return currentRole() == Role.MANAGER;
    }

    public boolean canView() {
        // 即使是 VIEWER 也能看,这里只检查"能拿到角色"而不是"角色非 null"
        // currentRole() 会抛异常保证 fail-safe,这里直接调用
        currentRole();
        return true;
    }
}
