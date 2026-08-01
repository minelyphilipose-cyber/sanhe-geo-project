# GEO Automation Extension

`geo-automation-extension` is the v2 stateless browser extension for self-media automation.

It is intentionally separate from `geo-env-extension`. The old extension remains untouched for the current production flow.

## Scope

- Keep only an install id and local worker endpoint.
- Pull current task context from `geo-automation-worker`.
- Use short-lived task tokens for backend payload and result APIs.
- Detect platform identity and report runtime status.
- Execute page filling in the current AdsPower profile.

## Non-goals

- No brand bind code.
- No `X-Ext-Token`.
- No long-lived backend credential.
- No legacy `/api/v1/extension/**` dependency.

## Local Development

```bash
npm install
npm run check
npm run build
```

Load `dist/` as an unpacked extension after build.
