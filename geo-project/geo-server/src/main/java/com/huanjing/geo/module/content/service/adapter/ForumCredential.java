package com.huanjing.geo.module.content.service.adapter;

import lombok.Data;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Data
public class ForumCredential {
    private String username;
    private String password;
    private String cookie;
    private String userAgent;
    private List<Account> accounts = new ArrayList<>();

    public Account pickAccount() {
        List<Account> candidates = normalizedAccounts();
        if (candidates.isEmpty()) {
            return new Account(username, password, cookie, userAgent, "active", null, null, null, null);
        }
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    public List<Account> normalizedAccounts() {
        List<Account> result = new ArrayList<>();
        if (accounts != null) {
            for (Account account : accounts) {
                if (account == null || (StringUtils.hasText(account.status()) && !"active".equalsIgnoreCase(account.status()))) {
                    continue;
                }
                if (StringUtils.hasText(account.cookie())
                        || (StringUtils.hasText(account.username()) && StringUtils.hasText(account.password()))) {
                    result.add(account);
                }
            }
        }
        if (result.isEmpty() && (StringUtils.hasText(cookie) || (StringUtils.hasText(username) && StringUtils.hasText(password)))) {
            result.add(new Account(username, password, cookie, userAgent, "active", null, null, null, null));
        }
        return result;
    }

    public boolean hasUsableCredential() {
        return !normalizedAccounts().isEmpty();
    }

    public record Account(
            String username,
            String password,
            String cookie,
            String userAgent,
            String status,
            String capturedAt,
            String expiresAt,
            String expirySource,
            String remark
    ) {
    }
}
