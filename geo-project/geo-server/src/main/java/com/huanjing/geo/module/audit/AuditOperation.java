package com.huanjing.geo.module.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method for audit logging.
 *
 * <p>Warning: Spring AOP does not intercept same-class self-invocation. Calls such as
 * {@code this.auditedMethod()} bypass this annotation; audited methods must be invoked through
 * a Spring proxy.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditOperation {
    String value();

    AuditMode mode() default AuditMode.ASYNC;

    boolean sensitive() default false;
}
