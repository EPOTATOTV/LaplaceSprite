# Laplace Console

拉普拉斯精灵的平台管理后台面板。当前包含 11.2 平台管理后台面板与 11.7 CI 指标看板与门禁两屏，其余 5 屏是占位路由。

技术栈：React 18 + TypeScript + Vite 5 + react-router-dom 6 + lucide-react。样式用 CSS Modules 加一套手写设计令牌，图表是手写 SVG，没有引入 UI 组件库和图表库。

## 跑起来

```bash
cd console
npm install
npm run dev          # http://localhost:5273
```

## 构建

```bash
npm run build        # tsc -b + vite build，产物在 dist/
npm run preview      # 预览构建产物
```

## 其它脚本

```bash
npm run typecheck    # tsc --noEmit
npm run lint         # eslint，零告警
```

## 数据来源

默认读 `src/data/fixtures.ts` 里的样例数据，不需要后端。

要接真实后端，在 `console/` 下建 `.env.local`：

```
VITE_API_BASE=http://127.0.0.1:8787
```

设置后页面会请求 `${VITE_API_BASE}/api/console`，响应结构对应 Java 侧 `io.laplace.sprite.core.model`（见 `src/types/console.ts`）。请求失败会静默回退到样例数据，不白屏。

## 目录

```
src/
  app/        路由、布局、左栏导航、顶栏、主题切换、路由过渡
  components/ 玻璃面板原语、状态标签、区块、手写 SVG 图表
  data/       fixtures、api、数据 Provider
  screens/    按设计稿编号分目录（dashboard / ci 已实现，其余为占位）
  styles/     tokens.css（设计令牌）、base.css（重置与排版）
  types/      后端 JSON 契约的 TS 镜像
```
