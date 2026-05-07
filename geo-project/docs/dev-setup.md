# Development Setup

## Cookie Master Key

The semi-auto self-media credential vault requires an explicit 32-byte master key in every environment.
Do not commit real keys to Git.

Generate a local development key:

```bash
openssl rand -base64 32
```

Set it before starting `geo-server`:

```bash
export GEO_COOKIE_MASTER_KEY_BASE64="<generated-value>"
```

PowerShell:

```powershell
$env:GEO_COOKIE_MASTER_KEY_BASE64="<generated-value>"
```

The previously reviewed placeholder value
`MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=` is revoked and must not be used in any environment.
