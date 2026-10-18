import type { RouteObject } from 'react-router-dom'

import DashboardScreen from '../screens/dashboard/DashboardScreen'
import CiScreen from '../screens/ci/CiScreen'
import NotFoundScreen from '../screens/NotFound'
import OrchestrationScreen from '../screens/orchestration'
import AdminViewScreen from '../screens/admin-view'
import ClientHudScreen from '../screens/client-hud'
import ModpackScreen from '../screens/modpack'
import LiveViewScreen from '../screens/live-view'
import BenchmarkScreen from '../screens/benchmark'

export const appRoutes: RouteObject[] = [
  { path: '/', element: <DashboardScreen /> },
  { path: '/orchestration', element: <OrchestrationScreen /> },
  { path: '/admin-view', element: <AdminViewScreen /> },
  { path: '/client-hud', element: <ClientHudScreen /> },
  { path: '/modpack', element: <ModpackScreen /> },
  { path: '/ci', element: <CiScreen /> },
  { path: '/live-view', element: <LiveViewScreen /> },
  { path: '/benchmark', element: <BenchmarkScreen /> },
  { path: '*', element: <NotFoundScreen /> },
]
