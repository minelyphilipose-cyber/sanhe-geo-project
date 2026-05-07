package com.huanjing.geo.module.content.credential.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.credential.audit.CredentialAuditHook;
import com.huanjing.geo.module.content.credential.crypto.CookieCryptoService;
import com.huanjing.geo.module.content.credential.crypto.LocalMasterKeyProvider;
import com.huanjing.geo.module.content.credential.dto.CookieCredentialCaptureCommand;
import com.huanjing.geo.module.content.credential.dto.CookieCredentialPlaintext;
import com.huanjing.geo.module.content.credential.entity.SelfMediaCookieCredential;
import com.huanjing.geo.module.content.entity.SelfMediaAccount;
import com.huanjing.geo.module.content.mapper.SelfMediaAccountMapper;
import com.huanjing.geo.module.content.mapper.SelfMediaCookieCredentialMapper;
import com.huanjing.geo.module.customer.access.BrandAccessAction;
import com.huanjing.geo.module.customer.access.BrandAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static com.huanjing.geo.module.content.credential.CredentialErrorCodes.CREDENTIAL_INTEGRITY_VIOLATION;
import static com.huanjing.geo.module.content.credential.CredentialErrorCodes.CREDENTIAL_PAYLOAD_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CredentialVaultServiceTest {

    private static final String MASTER_KEY = Base64.getEncoder().encodeToString(
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)
    );

    private SelfMediaCookieCredentialMapper credentialMapper;
    private SelfMediaAccountMapper accountMapper;
    private CredentialAuditHook auditHook;
    private BrandAccessService brandAccessService;
    private CredentialVaultService credentialVaultService;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SelfMediaAccount.class);
        credentialMapper = mock(SelfMediaCookieCredentialMapper.class);
        accountMapper = mock(SelfMediaAccountMapper.class);
        auditHook = mock(CredentialAuditHook.class);
        brandAccessService = mock(BrandAccessService.class);
        CookieCryptoService cryptoService = new CookieCryptoService(
                new LocalMasterKeyProvider("local-test", MASTER_KEY),
                new ObjectMapper()
        );
        credentialVaultService = new CredentialVaultService(
                credentialMapper,
                accountMapper,
                cryptoService,
                auditHook,
                brandAccessService
        );
    }

    @Test
    void storeCapturedCookiesClosesActiveVersionAndInsertsNextVersion() {
        SelfMediaAccount account = account();
        SelfMediaCookieCredential latest = new SelfMediaCookieCredential();
        latest.setVersion(2);
        when(accountMapper.lockById(20L)).thenReturn(20L);
        when(accountMapper.selectById(20L)).thenReturn(account);
        when(credentialMapper.selectLatestByAccountIdForUpdate(20L)).thenReturn(latest);

        credentialVaultService.storeCapturedCookies(new CookieCredentialCaptureCommand(
                20L,
                "[{\"name\":\"sessionid\",\"value\":\"secret\"}]",
                "Mozilla/5.0",
                "{\"browser\":\"chrome\"}",
                "{\"sessionid\":\"present\"}",
                99L
        ));

        verify(credentialMapper).closeActiveVersions(eq(20L), any());
        ArgumentCaptor<SelfMediaCookieCredential> credentialCaptor =
                ArgumentCaptor.forClass(SelfMediaCookieCredential.class);
        verify(credentialMapper).insert(credentialCaptor.capture());
        SelfMediaCookieCredential inserted = credentialCaptor.getValue();
        assertEquals(3, inserted.getVersion());
        assertEquals(10L, inserted.getBrandId());
        assertEquals("toutiao", inserted.getPlatform());
        assertEquals("brandId=10|accountId=20|platform=toutiao|version=3", inserted.getAadContext());
        assertNotNull(inserted.getCookieIvBase64());
        assertNotEquals("[{\"name\":\"sessionid\",\"value\":\"secret\"}]", inserted.getCookiesCiphertext());
        assertTrue(inserted.getEncryptedDek().contains("ciphertextBase64"));

        verify(accountMapper).update(eq(null), any());
        verify(brandAccessService).requireBrandAccess(10L, 99L, BrandAccessAction.MANAGE);
        verify(auditHook).onCredentialStored(any());
    }

    @Test
    void decryptActiveCookiesReturnsPlaintextFromFullCredentialOnly() {
        SelfMediaAccount account = account();
        when(accountMapper.lockById(20L)).thenReturn(20L);
        when(accountMapper.selectById(20L)).thenReturn(account);

        credentialVaultService.storeCapturedCookies(new CookieCredentialCaptureCommand(
                20L,
                "[{\"name\":\"z_c0\",\"value\":\"secret\"}]",
                "Mozilla/5.0",
                null,
                "{\"z_c0\":\"present\"}",
                99L
        ));
        ArgumentCaptor<SelfMediaCookieCredential> credentialCaptor =
                ArgumentCaptor.forClass(SelfMediaCookieCredential.class);
        verify(credentialMapper).insert(credentialCaptor.capture());
        SelfMediaCookieCredential fullCredential = credentialCaptor.getValue();
        fullCredential.setId(100L);
        when(credentialMapper.selectActiveFullByAccountId(20L)).thenReturn(fullCredential);

        CookieCredentialPlaintext plaintext = credentialVaultService.decryptActiveCookies(20L, 10L, 99L);

        assertEquals("[{\"name\":\"z_c0\",\"value\":\"secret\"}]", plaintext.cookiesJson());
        assertEquals(20L, plaintext.selfMediaAccountId());
        assertEquals("toutiao", plaintext.platform());
        verify(brandAccessService).requireBrandAccess(10L, 99L, BrandAccessAction.OPERATE);
        verify(auditHook).onCredentialDecrypted(any(), eq(99L));
    }

    @Test
    void decryptActiveCookiesRejectsUnexpectedBrand() {
        SelfMediaCookieCredential fullCredential = new SelfMediaCookieCredential();
        fullCredential.setId(100L);
        fullCredential.setSelfMediaAccountId(20L);
        fullCredential.setBrandId(10L);
        fullCredential.setPlatform("toutiao");
        fullCredential.setVersion(1);
        when(credentialMapper.selectActiveFullByAccountId(20L)).thenReturn(fullCredential);

        BizException ex = assertThrows(
                BizException.class,
                () -> credentialVaultService.decryptActiveCookies(20L, 11L, 99L)
        );

        assertEquals(CREDENTIAL_INTEGRITY_VIOLATION, ex.getCode());
        verify(auditHook).onCredentialAccessDenied(20L, 11L, 10L, 99L, "BRAND_MISMATCH");
    }

    @Test
    void destroyCredentialsRequiresManageAccessAndDestroysRows() {
        when(accountMapper.selectById(20L)).thenReturn(account());
        when(credentialMapper.destroyByAccountId(eq(20L), any())).thenReturn(2);

        int affected = credentialVaultService.destroyCredentials(20L, 10L, 99L);

        assertEquals(2, affected);
        verify(brandAccessService).requireBrandAccess(10L, 99L, BrandAccessAction.MANAGE);
        verify(auditHook).onCredentialDestroyed(20L, 99L, 2);
    }

    @Test
    void destroyCredentialsRejectsUnexpectedBrandBeforeDestroy() {
        when(accountMapper.selectById(20L)).thenReturn(account());

        BizException ex = assertThrows(
                BizException.class,
                () -> credentialVaultService.destroyCredentials(20L, 11L, 99L)
        );

        assertEquals(CREDENTIAL_INTEGRITY_VIOLATION, ex.getCode());
        verify(auditHook).onCredentialAccessDenied(20L, 11L, 10L, 99L, "BRAND_MISMATCH");
    }

    @Test
    void storeRejectsOversizedCookieJson() {
        String oversized = "x".repeat(49 * 1024);

        BizException ex = assertThrows(BizException.class, () ->
                credentialVaultService.storeCapturedCookies(new CookieCredentialCaptureCommand(
                        20L,
                        oversized,
                        null,
                        null,
                        null,
                        99L
                ))
        );

        assertEquals(CREDENTIAL_PAYLOAD_INVALID, ex.getCode());
    }

    private SelfMediaAccount account() {
        SelfMediaAccount account = new SelfMediaAccount();
        account.setId(20L);
        account.setBrandId(10L);
        account.setPlatform("toutiao");
        account.setAccountName("Toutiao Account");
        account.setStatus("active");
        return account;
    }
}
