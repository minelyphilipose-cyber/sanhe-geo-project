/**
 * 售前报表 TypeScript 类型入口。
 *
 * 对应 Java 包:com.huanjing.geo.module.presale.dto.snapshot
 * 对应 schema v1.2 根对象。
 *
 * 使用方式:
 *   import { MergedViewDTO, RawSnapshotDTO } from '@/types/presale';
 *
 * 命名规则:TS 类型名与 Java 类名一一对应(含 DTO 后缀),便于跨栈搜索和联调对账。
 */

import type { ComputedSnapshotDTO } from './computed';
import type { EditableContentDTO } from './editable';
import type { RawSnapshotDTO } from './raw';

/**
 * 完整三层快照(schema v1.2 根对象)。
 *
 * 用途:
 * - ops 调试接口返回的完整对象(GET /api/presale/versions/{versionNo}/raw-snapshot)
 * - JSON Schema v1.2 校验
 * - mergeSnapshot 输入
 *
 * 前端常规消费走 MergedViewDTO,不走此类型。
 */
export interface ReportSnapshotDTO {
  /** 固定 "v1.2"。 */
  schema_version: 'v1.2';
  raw_snapshot: RawSnapshotDTO;
  computed_snapshot: ComputedSnapshotDTO;
  editable_content: EditableContentDTO;
}

export * from './common';
export * from './raw';
export * from './computed';
export * from './editable';
export * from './merged';
