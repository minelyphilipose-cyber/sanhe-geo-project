/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{vue,js,ts,jsx,tsx}'],
  // 不让 Tailwind 覆盖 Element Plus 样式
  corePlugins: {
    preflight: false,
  },
  theme: {
    extend: {
      colors: {
        // 从旧代码提取的品牌色系
        brand: {
          50: '#EFF6FF',
          100: '#DBEAFE',
          200: '#BFDBFE',
          300: '#93C5FD',
          400: '#60A5FA',
          500: '#3B82F6',
          600: '#2563EB',
          700: '#1D4ED8',
          800: '#1E40AF',
          900: '#1E3A8A',
          950: '#172554',
        },
        // 深色背景系 (旧代码 top-banner 色)
        dark: {
          900: '#020617',
          800: '#0F172A',
          700: '#1E293B',
          600: '#334155',
        },
      },
      fontFamily: {
        sans: [
          'PingFang SC', 'Microsoft YaHei', 'Hiragino Sans GB',
          '-apple-system', 'BlinkMacSystemFont', 'Segoe UI',
          'Roboto', 'sans-serif',
        ],
        mono: ['JetBrains Mono', 'Fira Code', 'monospace'],
      },
      backdropBlur: {
        glass: '24px',
      },
    },
  },
  plugins: [],
}
