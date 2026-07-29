# Git 分支与发布流程

本项目采用双分支发布模型：

- `main`：稳定演示与发布分支。禁止直接开发或直接提交功能代码。
- `test`：日常开发、集成和验收分支。所有修改必须先进入该分支。

## 每次修改的标准流程

1. 开始工作前确认工作区干净，并切换到 `test`。
2. 获取远程更新，确保本地 `test` 与 `origin/test` 同步。
3. 在 `test` 上完成代码修改，不直接修改 `main`。
4. 根据改动范围完成验证，至少执行：
   - `npm.cmd run build`
   - `git diff --check`
   - 涉及界面时，使用浏览器检查相关桌面端和移动端页面。
   - 检查浏览器控制台，不得存在应用运行错误。
5. 仅在验证通过后提交到 `test`，提交信息使用清晰的 Conventional Commit 风格，例如：
   - `feat: add editable scorecard`
   - `fix: prevent mobile table overflow`
6. 将 `test` 推送到 `origin/test`，保留可回退的远程验收版本。
7. 切换到 `main`，先以 fast-forward-only 方式同步 `origin/main`。
8. 使用 `--no-ff` 将 `test` 合并到 `main`，保留明确的发布节点。
9. 在 `main` 上再次执行生产构建和必要的冒烟验证。
10. 验证通过后将 `main` 推送到 `origin/main`。
11. 切回 `test`，以 fast-forward-only 方式同步 `main`，使两个分支从同一发布节点继续演进。

## 执行原则

- 不在验证失败时合并或推送 `main`。
- 不使用强制推送覆盖 `main` 或 `test` 的历史。
- 不使用 `git reset --hard`、`git checkout --` 等方式清除用户修改。
- 每次发布后，本地默认停留在 `test` 分支，为下一次修改做好准备。
- 紧急修复原则上仍走 `test -> main`；只有用户明确授权时才允许例外。

## 推荐命令顺序

```powershell
git switch test
git pull --ff-only origin test

# 修改与验证
npm.cmd run build
git diff --check
git add <files>
git commit -m "feat: describe the change"
git push origin test

git switch main
git pull --ff-only origin main
git merge --no-ff test -m "merge: promote test to main"
npm.cmd run build
git push origin main

git switch test
git merge --ff-only main
git push origin test
```
