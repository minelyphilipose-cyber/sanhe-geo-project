# GEO Extension

Chrome MV3 companion extension for Sprint 2 semi-auto publishing.

## Scripts

```bash
npm install
npm test
npm run build
npm run build:test
npm run build:prod
```

Load `dist/` as an unpacked extension after `npm run build`.

## Build Profiles

- `npm run build:test`: uses local backend `http://127.0.0.1:8080` and local admin origins.
- `npm run build:prod`: uses the temporary production backend and admin origin `http://119.45.154.127`.

The profile switch is `VITE_GEO_EXTENSION_PROFILE=test|production`; `npm run build` defaults to the production profile because Vite build mode is production.

## Security Contracts

- Extension tokens live in `chrome.storage.local`; do not write them to page DOM or URLs.
- Cookie values must only be handled inside the service worker and fill-time content script message flow.
- Do not log cookies, fill tokens, or extension tokens. Shared logging helpers redact known token prefixes.
- The extension must never click the platform publish button. Operators publish manually; the extension may only fill fields and report state.
