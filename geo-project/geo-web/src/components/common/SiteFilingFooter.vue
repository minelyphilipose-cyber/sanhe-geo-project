<template>
  <footer class="site-filing-footer" aria-label="网站备案信息">
    <span>© 2024-2026 合肥市三合星链数字传媒科技有限公司 版权所有</span>
    <a
      href="https://beian.miit.gov.cn/"
      target="_blank"
      rel="noopener noreferrer"
    >
      皖ICP备2026007423号-3
    </a>
    <a
      class="police-filing-link"
      href="https://www.beian.gov.cn/portal/registerSystemInfo?recordcode=34018102340792"
      target="_blank"
      rel="noopener noreferrer"
    >
      <img :src="publicSecurityFilingIcon" alt="" class="police-filing-icon" />
      <span>皖公网安备34018102340792号</span>
    </a>
  </footer>
</template>

<script setup lang="ts">
import publicSecurityFilingIcon from '@/assets/brand/public-security-filing.png'
</script>

<style scoped>
.site-filing-footer {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 8px 18px;
  min-height: 48px;
  padding: 12px 20px;
  border-top: 1px solid #e2e8f0;
  background: rgba(255, 255, 255, 0.92);
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
  text-align: center;
}

.site-filing-footer a {
  color: #475569;
  text-decoration: none;
  transition: color 0.18s ease;
}

.site-filing-footer a:hover {
  color: #2563eb;
}

.police-filing-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.police-filing-icon {
  width: 16px;
  height: 16px;
  object-fit: contain;
  flex: 0 0 auto;
}
</style>
