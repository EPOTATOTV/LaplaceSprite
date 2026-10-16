import {
  Gamepad2,
  GitBranch,
  LayoutDashboard,
  MonitorPlay,
  Package,
  ShieldCheck,
  Waypoints,
} from 'lucide-react'
import type { LucideIcon } from 'lucide-react'

export interface NavEntry {
  /** 设计稿编号，留在导航里方便和规格对照 */
  code: string
  path: string
  label: string
  hint: string
  icon: LucideIcon
  /** 是否已经实现；未实现的走 ComingSoon */
  ready: boolean
}

export const NAV_ENTRIES: NavEntry[] = [
  {
    code: '11.2',
    path: '/',
    label: '平台管理后台',
    hint: '指标 · 运行 · 门禁',
    icon: LayoutDashboard,
    ready: true,
  },
  {
    code: '11.3',
    path: '/orchestration',
    label: '编排控制台',
    hint: '实例 · 剧本 · 日志',
    icon: Waypoints,
    ready: true,
  },
  {
    code: '11.4',
    path: '/admin-view',
    label: '管理员视图',
    hint: '旁观 · 判定 · 处置',
    icon: ShieldCheck,
    ready: true,
  },
  {
    code: '11.5',
    path: '/client-hud',
    label: '客户端 HUD',
    hint: '帧内叠加 · 探针',
    icon: Gamepad2,
    ready: true,
  },
  {
    code: '11.6',
    path: '/modpack',
    label: '整合包与模块',
    hint: '模块状态 · 插件配置',
    icon: Package,
    ready: true,
  },
  {
    code: '11.7',
    path: '/ci',
    label: 'CI 指标看板',
    hint: '流水线 · 阈值门禁',
    icon: GitBranch,
    ready: true,
  },
  {
    code: '11.8',
    path: '/live-view',
    label: '画面监控墙',
    hint: '一键查看 · 多路',
    icon: MonitorPlay,
    ready: true,
  },
]
