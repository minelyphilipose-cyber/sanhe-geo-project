package com.huanjing.geo.module.content.wechat;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.config.WechatOpenPlatformProperties;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WechatMessageCryptoServiceTest {

    @Test
    void encryptReplyCanBeDecryptedBack() {
        WechatMessageCryptoService service = new WechatMessageCryptoService(properties());
        String reply = "<xml><ToUserName><![CDATA[user]]></ToUserName><Content><![CDATA[ok]]></Content></xml>";

        String encrypted = service.encryptReply(reply, "1710000000", "nonce-1");
        Map<String, String> xml = WechatXmlParser.parse(encrypted);

        String decrypted = service.decryptIfNeeded(
                encrypted,
                xml.get("MsgSignature"),
                xml.get("TimeStamp"),
                xml.get("Nonce")
        );

        assertThat(decrypted).isEqualTo(reply);
    }

    @Test
    void decryptRejectsMissingSignatureParams() {
        WechatMessageCryptoService service = new WechatMessageCryptoService(properties());
        String encrypted = service.encryptReply("<xml><Content><![CDATA[ok]]></Content></xml>", "1710000000", "nonce-1");

        assertThatThrownBy(() -> service.decryptIfNeeded(encrypted, null, "1710000000", "nonce-1"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("wechat signature params missing");
    }

    @Test
    void decryptRejectsInvalidSignature() {
        WechatMessageCryptoService service = new WechatMessageCryptoService(properties());
        String encrypted = service.encryptReply("<xml><Content><![CDATA[ok]]></Content></xml>", "1710000000", "nonce-1");

        assertThatThrownBy(() -> service.decryptIfNeeded(encrypted, "bad-signature", "1710000000", "nonce-1"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("wechat message signature invalid");
    }

    @Test
    void decryptRejectsMismatchedAppid() {
        WechatOpenPlatformProperties senderProperties = properties();
        senderProperties.setComponentAppid("wx-other-appid");
        WechatMessageCryptoService sender = new WechatMessageCryptoService(senderProperties);
        WechatMessageCryptoService receiver = new WechatMessageCryptoService(properties());

        String encrypted = sender.encryptReply("<xml><Content><![CDATA[ok]]></Content></xml>", "1710000000", "nonce-1");
        Map<String, String> xml = WechatXmlParser.parse(encrypted);

        assertThatThrownBy(() -> receiver.decryptIfNeeded(
                encrypted,
                xml.get("MsgSignature"),
                xml.get("TimeStamp"),
                xml.get("Nonce")
        ))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("wechat appid mismatch");
    }

    @Test
    void verifyUrlSignatureAcceptsValidSignature() {
        WechatMessageCryptoService service = new WechatMessageCryptoService(properties());

        service.verifyUrlSignature(plainSignature("test-token", "1710000000", "nonce-1", "echo"), "1710000000", "nonce-1", "echo");
    }

    @Test
    void verifyUrlSignatureRejectsInvalidSignature() {
        WechatMessageCryptoService service = new WechatMessageCryptoService(properties());

        assertThatThrownBy(() -> service.verifyUrlSignature("bad-signature", "1710000000", "nonce-1", "echo"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("wechat url signature invalid");
    }

    private WechatOpenPlatformProperties properties() {
        WechatOpenPlatformProperties properties = new WechatOpenPlatformProperties();
        byte[] key = new byte[32];
        for (int i = 0; i < key.length; i++) {
            key[i] = (byte) i;
        }
        properties.setComponentAppid("wx514c6537de79de97");
        properties.setToken("test-token");
        properties.setEncodingAesKey(Base64.getEncoder().encodeToString(key).replace("=", ""));
        return properties;
    }

    private String plainSignature(String... values) {
        try {
            Arrays.sort(values);
            byte[] bytes = MessageDigest.getInstance("SHA-1")
                    .digest(String.join("", values).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
