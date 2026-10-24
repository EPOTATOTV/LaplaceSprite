import {
  Gamepad2,
  GitBranch,
  LayoutDashboard,
  MonitorPlay,
  Package,
  ShieldCheck,
  Trophy,
  Waypoints,
} from 'lucide-react'
import type { LucideIcon } from 'lucide-react'

import type { MessageKey } from '../i18n/messages'

export interface NavEntry {
  /** 设计稿编号，留在导航里方便和规格对照 */
  code: string
  path: string
  labelKey: MessageKey
  hintKey: MessageKey
  icon: LucideIcon
  /** 是否已经实现；未实现的走 ComingSoon */
  ready: boolean
}

export const NAV_ENTRIES: NavEntry[] = [
  {
    code: '11.2',
    path: '/',
    labelKey: 'nav.dashboard.label',
    hintKey: 'nav.dashboard.hint',
    icon: LayoutDashboard,
    ready: true,
  },
  {
    code: '11.3',
    path: '/orchestration',
    labelKey: 'nav.orchestration.label',
    hintKey: 'nav.orchestration.hint',
    icon: Waypoints,
    ready: true,
  },
  {
    code: '11.4',
    path: '/admin-view',
    labelKey: 'nav.admin-view.label',
    hintKey: 'nav.admin-view.hint',
    icon: ShieldCheck,
    ready: true,
  },
  {
    code: '11.5',
    path: '/client-hud',
    labelKey: 'nav.client-hud.label',
    hintKey: 'nav.client-hud.hint',
    icon: Gamepad2,
    ready: true,
  },
  {
    code: '11.6',
    path: '/modpack',
    labelKey: 'nav.modpack.label',
    hintKey: 'nav.modpack.hint',
    icon: Package,
    ready: true,
  },
  {
    code: '11.7',
    path: '/ci',
    labelKey: 'nav.ci.label',
    hintKey: 'nav.ci.hint',
    icon: GitBranch,
    ready: true,
  },
  {
    code: '11.8',
    path: '/live-view',
    labelKey: 'nav.live-view.label',
    hintKey: 'nav.live-view.hint',
    icon: MonitorPlay,
    ready: true,
  },
  {
    code: '11.9',
    path: '/benchmark',
    labelKey: 'nav.benchmark.label',
    hintKey: 'nav.benchmark.hint',
    icon: Trophy,
    ready: true,
  },
]
