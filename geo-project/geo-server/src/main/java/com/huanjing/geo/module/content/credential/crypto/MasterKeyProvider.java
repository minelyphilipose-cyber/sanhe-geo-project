package com.huanjing.geo.module.content.credential.crypto;

public interface MasterKeyProvider {
    String keyId();

    EncryptedData encryptDek(byte[] dek, String canonicalAad);

    byte[] decryptDek(EncryptedData encryptedDek, String canonicalAad);
}
