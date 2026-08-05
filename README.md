# Cloud Store Fabric Mod

适用于 Minecraft Java Edition 26.1.x 的 Fabric 客户端模组。它直接使用云仓库
`src/webServer.js` 已有的 Web API，在游戏内查看库存，并发起存入、取出操作。
组织管理、流水、别名、订阅等复杂功能仍在网页端完成。

## 使用

1. 安装 Minecraft 26.1.x、Fabric Loader 0.19.0+ 和对应版本的 Fabric API。
2. 把 `build/libs/cloud-store-0.3.0.jar` 放进客户端的 `mods` 目录，并删除旧版本的 Cloud Store JAR。
3. 进入游戏后按 `O` 打开云仓库，点击右下角“设置”。
4. 填写云仓库 Web 服务地址，例如 `http://192.168.1.10:8787`。
5. 填写在游戏内通过 `/msg <机器人> 设置密码 <密码>` 设置的 Web 登录密码。

配置保存在游戏实例的 `config/cloud-store.json`。登录密码目前以明文保存在本地配置中，
不要在公用电脑上保存账号密码。服务端支持 HTTPS 时，应优先使用 HTTPS 地址。

## 操作

- 按 `O` 打开库存界面。
- 输入文字可按物品名称、物品 ID 或短码筛选；上方分类栏与创造模式物品分类一致。
- 多个可访问仓库时，点击仓库名称循环切换。
- 潜影盒本身不会显示，盒内物品会与散装物品合并计数。
- 点击物品填写数量会加入取货清单，可继续添加、修改或移除其他物品，再一次性提交。
- “领取玩家”留空时领取给自己，填写其他游戏 ID 时可代取给该玩家。
- 点击“存入”发起远程存储，后续传送和交付流程与网页端相同。

库存会缓存在游戏实例的 `config/cloud-store-cache.json`。再次打开界面时先显示缓存，
并在后台刷新实际库存；缓存不包含登录密码。

## 构建

需要 Java 25：

```powershell
.\gradlew.bat build
```

可安装 JAR 输出到 `build/libs/cloud-store-0.3.0.jar`。

## 使用的接口

- `GET /api/me`
- `POST /api/login`
- `GET /api/inventory?owner=...`
- `POST /api/action/deposit`
- `POST /api/action/withdraw`

客户端保存并复用后端返回的 `cs_sid` Cookie；每次重启游戏后会使用配置中的登录密码重新建立会话。
