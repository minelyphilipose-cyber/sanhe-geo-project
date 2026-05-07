# GEO Extension

Chrome MV3 companion extension for Sprint 2 semi-auto publishing.

## Scripts

```bash
npm install
npm test
npm run build
```

Load `dist/` as an unpacked extension after `npm run build`.

## Security Contracts

- Extension tokens live in `chrome.storage.local`; do not write them to page DOM or URLs.
- Cookie values must only be handled inside the service worker and fill-time content script message flow.
- Do not log cookies, fill tokens, or extension tokens. Shared logging helpers redact known token prefixes.
- The extension must never click the platform publish button. Operators publish manually; the extension may only fill fields and report state.
