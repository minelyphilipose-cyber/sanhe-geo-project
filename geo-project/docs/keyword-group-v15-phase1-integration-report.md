# Keyword Group V1.5 Phase 1 Integration Report

Date: 2026-04-28

## Environment

- Backend: `http://localhost:8080`
- Frontend: `http://localhost:3000`
- Login account: `admin / admin123`
- API check script: `docs/keyword-group-v15-integration-api-check.ps1`

## Fix Applied Before Testing

- Fixed `GET /api/keyword-groups/type-configs` route collision by moving the static route before the `/{id}` detail route and constraining the detail route to numeric ids.
- Reason: Spring matched `/type-configs` as `/{id}` and attempted to parse it as `Long`, returning 500.

## API Integration Results

| Case | Result | Evidence |
| --- | --- | --- |
| 1. brand basic preview | Pass | Expected 125, returned `totalAvailable=125`, `keywords.length=125` |
| 2. decision + area | Pass | Expected 81, returned `totalAvailable=81`, `keywords.length=81` |
| 3. transaction suffix-only | Pass | Expected 12, returned `totalAvailable=12`, `keywords.length=12` |
| 4. comparison normal | Pass | Expected 120, returned `totalAvailable=120`, `keywords.length=120` |
| 5. comparison over 1000 | Pass | Returned business error: `预计生成 1920 条，超过上限 1000，请减少选词` |
| 6. qa naturalness basis | Pass | Expected 40, returned `totalAvailable=40`, `keywords.length=40` |
| 7. function industry filter | Pass | `appliance` prefix count = 23, no other industry tags returned, preview count = 125 |
| 8. function industry switch basis | Pass | `door_window` prefix count = 23; common words retained in filter simulation |
| 10. legacy search detail | Pass (API) | Search group id `1`, `type=search`, `legacyType=true`, label `搜索词(历史)` |
| 11. error code parsing basis | Pass (API) | Missing `coreWordsB` returns `COMPARE_CORE_B_REQUIRED: coreWordsB is required` |

Notes:

- PowerShell displays Chinese response text with mojibake because the endpoint response lacks an explicit charset. Counts and status checks are unaffected. Browser/axios rendering should use the UTF-8 payload normally.
- Case 9 and the interactive part of case 10 are UI state-machine checks. They were reviewed from source and still need a browser/manual confirmation pass:
  - `lastEditState` snapshot/restore uses deep copy.
  - `openEdit` hydrates form and calls `loadOptionsByType`, without calling `handleUserTypeChange`.
  - Legacy type selector injects the history option and upgrade confirm path.

## Verification Commands

```powershell
cd D:\code\sanhe-geo-project\geo-project\geo-server
mvn -DskipTests compile

cd D:\code\sanhe-geo-project
powershell -ExecutionPolicy Bypass -File geo-project/docs/keyword-group-v15-integration-api-check.ps1
```

## Remaining Before Batch 8 Naturalness Evaluation

- Manual/browser check for case 9: `lastEditState` decision -> transaction -> decision -> comparison behavior.
- Manual/browser check for case 10: legacy `search` edit flow and upgrade confirmation dialog.
- Case 12 remains deferred until #7 maintenance page supports temporary words.
