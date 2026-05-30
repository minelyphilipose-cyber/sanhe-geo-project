import fs from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const scanRoots = [
  'geo-server/src/main/java',
  'geo-local-helper',
  'geo-env-extension',
  'geo-web/src',
]
const ignoreDirs = new Set(['node_modules', 'target', 'dist', 'build', '.git'])
const sourceExtensions = new Set(['.java', '.js', '.mjs', '.ts', '.vue', '.html', '.md'])
const logPattern = /\b(log|logger)\.(info|warn|error|debug|trace)\b|\bconsole\.(log|warn|error|debug)\b/i
const sensitivePattern = /accessToken|helperAccessToken|pairingCode|hmacSecret|cookie|authorization|x-ext-token|fillToken|backendToken|X-Geo-Helper-Access|token/i

async function walk(dir, results) {
  const entries = await fs.readdir(dir, { withFileTypes: true })
  for (const entry of entries) {
    if (ignoreDirs.has(entry.name)) continue
    const fullPath = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      await walk(fullPath, results)
      continue
    }
    if (!sourceExtensions.has(path.extname(entry.name))) continue
    results.push(fullPath)
  }
}

async function main() {
  const files = []
  for (const root of scanRoots) {
    await walk(path.join(repoRoot, root), files)
  }

  const findings = []
  for (const file of files) {
    const content = await fs.readFile(file, 'utf8')
    const lines = content.split(/\r?\n/)
    lines.forEach((line, index) => {
      if (logPattern.test(line) && sensitivePattern.test(line)) {
        findings.push({
          file: path.relative(repoRoot, file).replaceAll(path.sep, '/'),
          line: index + 1,
          text: line.trim(),
        })
      }
    })
  }

  console.log(JSON.stringify({
    securityLogScan: findings.length === 0 ? 'ok' : 'review_required',
    scannedFiles: files.length,
    findingCount: findings.length,
    findings,
  }, null, 2))
  if (findings.length > 0) process.exitCode = 1
}

main().catch((error) => {
  console.error(JSON.stringify({
    securityLogScan: 'failed',
    error: error.message,
  }, null, 2))
  process.exitCode = 1
})
