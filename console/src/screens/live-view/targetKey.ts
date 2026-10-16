import type { LiveViewTarget } from '../../types'

/** 一个查看目标在列表与画面墙里共用的键 */
export function targetKey(target: LiveViewTarget): string {
  return `${target.instanceId}/${target.simId}`
}
