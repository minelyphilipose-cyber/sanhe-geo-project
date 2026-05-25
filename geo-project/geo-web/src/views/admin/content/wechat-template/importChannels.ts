export type ImportChannelValue = 'source_135' | 'xiumi' | 'wechat_history'

export interface ImportChannel {
  value: ImportChannelValue
  sourceType: string
  name: string
  description: string
  disabled: boolean
}

export const importChannels: ImportChannel[] = [
  {
    value: 'source_135',
    sourceType: 'source_135',
    name: '135 编辑器',
    description: '支持自动拆分模板、合并相似样式，并标出需要确认的片段。',
    disabled: false,
  },
  {
    value: 'xiumi',
    sourceType: 'xiumi',
    name: '秀米导入',
    description: '后续支持专用识别规则，当前请先使用通用 HTML 导入。',
    disabled: true,
  },
  {
    value: 'wechat_history',
    sourceType: 'wechat_history',
    name: '公众号历史文章',
    description: '后续支持从已发布文章提取样式。',
    disabled: true,
  },
]

export const defaultImportChannel = importChannels.find((channel) => !channel.disabled) || importChannels[0]

export function findImportChannel(value: string) {
  return importChannels.find((channel) => channel.value === value)
}
