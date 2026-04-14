import rawRegion from '@/assets/pca-code.json'

export interface RegionTreeNode {
  code: string
  name: string
  children?: RegionTreeNode[]
}

export interface RegionOption {
  value: string
  label: string
  children?: RegionOption[]
}

export interface RegionPayload {
  provinceCode?: string | null
  provinceName?: string | null
  cityCode?: string | null
  cityName?: string | null
  districtCode?: string | null
  districtName?: string | null
  displayName?: string
}

const regionTree = rawRegion as RegionTreeNode[]

function mapOption(node: RegionTreeNode): RegionOption {
  return {
    value: node.code,
    label: node.name,
    children: node.children?.map(mapOption),
  }
}

export const chinaRegionOptions: RegionOption[] = regionTree.map(mapOption)

export function regionPayloadFromCodes(codes: string[]): RegionPayload {
  if (!codes || !codes.length) {
    return {}
  }
  const province = regionTree.find((item) => item.code === codes[0])
  const city = province?.children?.find((item) => item.code === codes[1])
  const district = city?.children?.find((item) => item.code === codes[2])
  const names = [province?.name, city?.name, district?.name].filter((item): item is string => !!item)
  return {
    provinceCode: province?.code,
    provinceName: province?.name,
    cityCode: city?.code,
    cityName: city?.name,
    districtCode: district?.code,
    districtName: district?.name,
    displayName: names.length ? names.join(' ') : undefined,
  }
}

export function regionCodesFromPayload(payload: Partial<RegionPayload>): string[] {
  const values: string[] = []
  if (payload.provinceCode) values.push(payload.provinceCode)
  if (payload.cityCode) values.push(payload.cityCode)
  if (payload.districtCode) values.push(payload.districtCode)
  return values
}

export function regionDisplayFromCodes(codes: string[]): string {
  return regionPayloadFromCodes(codes).displayName || ''
}

export function regionDisplayFromPayload(payload: Partial<RegionPayload>): string {
  const names = [payload.provinceName, payload.cityName, payload.districtName]
    .filter((name): name is string => !!name && String(name).trim().length > 0)
  return names.join(' ')
}
