package com.huanjing.geo.module.content.wechat;

import com.huanjing.geo.common.exception.BizException;
import com.huanjing.geo.module.content.config.WechatOpenPlatformProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WechatMessageCryptoService {
    private static final int BLOCK_SIZE = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final WechatOpenPlatformProperties properties;

    public boolean isEncrypted(String rawXml) {
        return WechatXmlParser.parse(rawXml).containsKey("Encrypt");
    }

    public String decryptIfNeeded(String rawXml, String msgSignature, String timestamp, String nonce) {
        Map<String, String> xml = WechatXmlParser.parse(rawXml);
        String encrypted = xml.get("Encrypt");
        if (!StringUtils.hasText(encrypted)) {
            return rawXml;
        }
        verifySignature(encrypted, msgSignature, timestamp, nonce);
        try {
            byte[] key = aesKey();
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(Arrays.copyOfRange(key, 0, 16)));
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encrypted));
            byte[] plain = removePkcs7Padding(decrypted);
            int xmlLength = ByteBuffer.wrap(plain, 16, 4).getInt();
            String messageXml = new String(plain, 20, xmlLength, StandardCharsets.UTF_8);
            String appid = new String(plain, 20 + xmlLength, plain.length - 20 - xmlLength, StandardCharsets.UTF_8);
            if (StringUtils.hasText(properties.getComponentAppid())
                    && !properties.getComponentAppid().equals(appid)) {
                throw new BizException(400, "wechat appid mismatch");
            }
            return messageXml;
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(400, "wechat message decrypt failed");
        }
    }

    public String encryptReply(String replyXml, String timestamp, String nonce) {
        if (!StringUtils.hasText(replyXml) || !replyXml.trim().startsWith("<xml>")) {
            return replyXml;
        }
        if (!StringUtils.hasText(properties.getComponentAppid())) {
            throw new BizException(500, "wechat component appid missing");
        }
        String ts = StringUtils.hasText(timestamp) ? timestamp : String.valueOf(System.currentTimeMillis() / 1000);
        String nn = StringUtils.hasText(nonce) ? nonce : "nonce";
        try {
            byte[] key = aesKey();
            byte[] random = new byte[16];
            RANDOM.nextBytes(random);
            byte[] xmlBytes = replyXml.getBytes(StandardCharsets.UTF_8);
            byte[] appidBytes = properties.getComponentAppid().getBytes(StandardCharsets.UTF_8);
            ByteBuffer buffer = ByteBuffer.allocate(20 + xmlBytes.length + appidBytes.length + BLOCK_SIZE);
            buffer.put(random);
            buffer.putInt(xmlBytes.length);
            buffer.put(xmlBytes);
            buffer.put(appidBytes);
            byte[] padded = addPkcs7Padding(Arrays.copyOf(buffer.array(), buffer.position()));

            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(Arrays.copyOfRange(key, 0, 16)));
            String encrypted = Base64.getEncoder().encodeToString(cipher.doFinal(padded));
            String signature = signature(encrypted, ts, nn);
            return "<xml>"
                    + "<Encrypt><![CDATA[" + encrypted + "]]></Encrypt>"
                    + "<MsgSignature><![CDATA[" + signature + "]]></MsgSignature>"
                    + "<TimeStamp>" + ts + "</TimeStamp>"
                    + "<Nonce><![CDATA[" + nn + "]]></Nonce>"
                    + "</xml>";
        } catch (Exception ex) {
            throw new BizException(500, "wechat message encrypt failed");
        }
    }

    public void verifyUrlSignature(String signature, String timestamp, String nonce, String echostr) {
        if (!StringUtils.hasText(properties.getToken())) {
            throw new BizException(500, "wechat token not configured");
        }
        if (!StringUtils.hasText(signature) || !StringUtils.hasText(timestamp)
                || !StringUtils.hasText(nonce) || !StringUtils.hasText(echostr)) {
            throw new BizException(400, "wechat url signature params missing");
        }
        String expected = plainSignature(timestamp, nonce, echostr);
        if (!expected.equalsIgnoreCase(signature)) {
            throw new BizException(400, "wechat url signature invalid");
        }
    }

    private void verifySignature(String encrypted, String msgSignature, String timestamp, String nonce) {
        if (!StringUtils.hasText(properties.getToken())) {
            throw new BizException(500, "wechat token not configured");
        }
        if (!StringUtils.hasText(msgSignature) || !StringUtils.hasText(timestamp) || !StringUtils.hasText(nonce)) {
            throw new BizException(400, "wechat signature params missing");
        }
        String expected = signature(encrypted, timestamp, nonce);
        if (!expected.equalsIgnoreCase(msgSignature)) {
            throw new BizException(400, "wechat message signature invalid");
        }
    }

    private String signature(String encrypted, String timestamp, String nonce) {
        return sha1Sorted(properties.getToken(), timestamp, nonce, encrypted);
    }

    private String plainSignature(String timestamp, String nonce, String echostr) {
        return sha1Sorted(properties.getToken(), timestamp, nonce, echostr);
    }

    private String sha1Sorted(String... values) {
        try {
            String[] parts = values;
            Arrays.sort(parts);
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(String.join("", parts).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new BizException(500, "wechat message signature failed");
        }
    }

    private byte[] aesKey() {
        String key = properties.getEncodingAesKey();
        if (!StringUtils.hasText(key) || key.length() != 43) {
            throw new BizException(500, "wechat encoding aes key missing");
        }
        return Base64.getDecoder().decode(key + "=");
    }

    private byte[] addPkcs7Padding(byte[] data) {
        int amount = BLOCK_SIZE - data.length % BLOCK_SIZE;
        if (amount == 0) {
            amount = BLOCK_SIZE;
        }
        byte[] padded = Arrays.copyOf(data, data.length + amount);
        Arrays.fill(padded, data.length, padded.length, (byte) amount);
        return padded;
    }

    private byte[] removePkcs7Padding(byte[] data) {
        int pad = data[data.length - 1] & 0xff;
        if (pad < 1 || pad > BLOCK_SIZE) {
            throw new BizException(400, "wechat message padding invalid");
        }
        return Arrays.copyOf(data, data.length - pad);
    }
}
