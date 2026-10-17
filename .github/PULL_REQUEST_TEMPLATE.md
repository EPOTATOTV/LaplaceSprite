# 变更说明

## 改动类型

- [ ] 新功能
- [ ] 缺陷修复
- [ ] 重构 / 性能
- [ ] 文档
- [ ] CI / 构建

## 做了什么

<!-- 一到三句话说明动机与结果，不要只列文件名。 -->

## 验证

<!-- 贴出实际跑过的命令与关键输出。 -->

```
gradle test
laplace run --suite legit --players 500
laplace gate
```

## 检查项

- [ ] 新增/修改的逻辑有对应单元测试
- [ ] `gradle test` 全绿
- [ ] 四条平台门禁通过（legit 0 误报 / cheat ≥95% / boundary 全通过 / gate 退出 0）
- [ ] 未提交密钥、构建产物或玩家数据
- [ ] 行为有变化时同步了 `docs/`
- [ ] 新增模块时更新了 `settings.gradle` 与 `README.md` 模块表

## 相关 issue

<!-- Closes #123 -->
