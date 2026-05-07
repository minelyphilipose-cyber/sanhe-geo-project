# Credential Data Migration Notes

## Encrypted DEK JSON

Sprint 1 uses `EncryptedData.cipherAlg` in the serialized `encrypted_dek` JSON.
Earlier local development data created during review may contain the old `algorithm` field name.

Before keeping any dev/test database across this change, clear rows created by the old draft:

```sql
DELETE FROM self_media_cookie_credential;
```

There is no production data for this schema yet. Future KMS migrations must preserve support for
the current `cipherAlg`, `ivBase64`, and `ciphertextBase64` fields.
