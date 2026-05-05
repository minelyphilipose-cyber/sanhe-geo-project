package com.huanjing.geo.module.content.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class SelfMediaAccountMapperIT {

    @Autowired
    private SelfMediaAccountMapper mapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void insertAndQueryWithJsonFields() {
        Long brandId = resolveBrandId();
        String platformAccountId = "test-step232-" + System.currentTimeMillis();

        SelfMediaAccount account = new SelfMediaAccount();
        account.setBrandId(brandId);
        account.setPlatform("wechat_mp");
        account.setPlatformAccountId(platformAccountId);
        account.setAccountName("Step 2.3.2 IT");
        account.setStatus("active");
        account.setScopeJson("{\"scope\":[\"draft\"]}");
        account.setExtraJson("{\"legacy_id\":999}");

        try {
            mapper.insert(account);
            SelfMediaAccount loaded = mapper.selectById(account.getId());

            assertNotNull(loaded);
            assertNotNull(loaded.getScopeJson());
            assertNotNull(loaded.getExtraJson());
            assertTrue(loaded.getScopeJson().contains("draft"));
            assertTrue(loaded.getExtraJson().contains("999"));
        } finally {
            mapper.delete(new LambdaQueryWrapper<SelfMediaAccount>()
                    .eq(SelfMediaAccount::getPlatform, "wechat_mp")
                    .eq(SelfMediaAccount::getPlatformAccountId, platformAccountId));
        }
    }

    private Long resolveBrandId() {
        List<Long> brandIds = jdbcTemplate.queryForList(
                "SELECT id FROM brand ORDER BY id LIMIT 1",
                Long.class
        );
        if (!brandIds.isEmpty()) {
            return brandIds.get(0);
        }
        long brandId = 9900232L;
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=0");
        jdbcTemplate.update("""
                INSERT IGNORE INTO brand (id, company_id, industry, brand_name, brand_slug, status)
                VALUES (?, ?, 'general', 'Step 2.3.2 IT Brand', 'step-232-it-brand', 'active')
                """, brandId, brandId);
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS=1");
        return brandId;
    }
}
