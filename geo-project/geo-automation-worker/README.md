# GEO Automation Worker

`geo-automation-worker` is the v2 local automation executor for AdsPower self-media publishing.

It is intentionally separate from `geo-local-helper`. The old helper remains untouched for the current production flow.

## Scope

- Pair with backend v2 worker APIs.
- Sign backend requests with a body-bound HMAC contract.
- Manage AdsPower Local API access.
- Claim v2 automation tasks.
- Start the required AdsPower profile.
- Provide current task context to `geo-automation-extension`.
- Forward extension runtime status.
- Report task stage, success, and failure.

## Non-goals

- No `ExtensionSession`.
- No brand-bound extension token.
- No legacy `/api/v1/local-agent/**` dependency.
- No production task claim until the backend v2 gate is ready.

## Local Development

```bash
npm install
npm run check
npm start
```

Copy `config.example.json` to `config.local.json` for local settings.
