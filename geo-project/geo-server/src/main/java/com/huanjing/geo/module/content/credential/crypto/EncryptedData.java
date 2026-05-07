package com.huanjing.geo.module.content.credential.crypto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EncryptedData {
    private String keyId;
    private String cipherAlg;
    private String ivBase64;
    private String ciphertextBase64;
}
