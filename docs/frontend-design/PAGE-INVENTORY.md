# 前端页面与路由清单

状态含义：`Current` 可由现有接口支持；`Partial` 有明确限制；`API GAP` 需先补后端；`Legal` 需法律内容。

## 公共页面

| 路由 | 页面 | 状态 | 审阅板 |
| --- | --- | --- | --- |
| `/` | 市场首页 | Current | 01 |
| `/marketplace` | OFFER / REQUEST 发现与筛选 | Current | 01 |
| `/categories/:code` | 分类落地页 | Current | 01 |
| `/tickets/:ticketId` | Listing 详情与响应入口 | Partial | 02 |
| `/workers` | 服务者目录 | Current | 03 |
| `/workers/:workerId` | 服务者公开资料 | Partial | 03 |
| `/how-it-works` | 双向市场流程说明 | Current | 17 |
| `/terms` | 服务条款 | Legal | 17 |
| `/privacy` | 隐私政策 | Legal | 17 |
| `/login` | 登录 | Current | 04 |
| `/register` | 注册 | Partial | 04 |
| `/account` | 当前账号摘要 | Current | 04 |

`/app` 登录后跳转到上次使用的工作区；如果没有本地记录，默认进入 Client 工作区。这个偏好只保存在前端，不改变账号角色。

## Client 工作区

| 路由 | 页面 | 状态 | 审阅板 |
| --- | --- | --- | --- |
| `/app/client` | Client 总览 | Partial | 06 |
| `/app/client/requests` | 我的 REQUEST | Current | 07 |
| `/app/client/requests/new` | 新建 REQUEST | Current | 05 |
| `/app/client/requests/:ticketId` | REQUEST 管理详情 | Current | 07 |
| `/app/client/requests/:ticketId/edit` | 编辑 REQUEST | Current | 05、07 |
| `/app/client/requests/:ticketId/applications` | 收到的 Proposal | Current | 08 |
| `/app/client/applications` | 我发给 OFFER 的 Service request | Partial | 08 |
| `/app/client/engagements` | Client 履约列表 | Current | 09 |
| `/app/client/engagements/:id` | Client 履约详情 | Current | 09、18 |
| `/app/client/engagements/:id/payment` | Mock / Stripe 付款分支 | Partial | 09、18 |
| `/app/client/refunds` | 我的退款 | Current | 09、12 |
| `/app/client/refunds/:id` | 退款详情 | Current | 09、12 |
| `/app/client/reputation` | 收到与写出的评价 | Current | 12 |

## Worker 工作区

| 路由 | 页面 | 状态 | 审阅板 |
| --- | --- | --- | --- |
| `/app/worker` | Worker 总览 | Partial | 06 |
| `/app/worker/profile` | 资料编辑与公开预览 | API GAP | 11 |
| `/app/worker/offers` | 我的 OFFER | Current | 07 |
| `/app/worker/offers/new` | 新建 OFFER | Current | 05 |
| `/app/worker/offers/:ticketId` | OFFER 管理详情 | Current | 07 |
| `/app/worker/offers/:ticketId/edit` | 编辑 OFFER | Current | 05、07 |
| `/app/worker/offers/:ticketId/applications` | 收到的 Service request | Current | 08 |
| `/app/worker/proposals` | 我发给 REQUEST 的 Proposal | Partial | 08 |
| `/app/worker/engagements` | Worker 履约列表 | Current | 10 |
| `/app/worker/engagements/:id` | Worker 履约、证据与交付 | Current | 10、18 |
| `/app/worker/credentials` | 我的资质 | Current | 11 |
| `/app/worker/credentials/new` | 新增资质 | Current | 11 |
| `/app/worker/earnings` | 收入与结算 | Partial | 12 |
| `/app/worker/earnings/:settlementId` | Settlement 详情 | Current | 12 |
| `/app/worker/reputation` | 收到与写出的评价 | Current | 12 |

资质重提使用列表中的 Drawer，不单独建立深层路由。

## Admin 工作区

| 路由 | 页面 | 状态 | 审阅板 |
| --- | --- | --- | --- |
| `/admin` | 运营总览 | Partial | 15 |
| `/admin/credentials` | 资质审核队列与详情 | Current | 13、16 |
| `/admin/disputes` | 争议队列 | API GAP | 13 |
| `/admin/disputes/:engagementId` | 已知 Engagement 的争议详情与裁决 | Current | 13 |
| `/admin/finance` | 财务总览 | Partial | 14 |
| `/admin/finance/settlements/:id` | Settlement 详情 | Current | 14 |
| `/admin/finance/batches` | Batch 列表、运行与结果 | Current | 14 |
| `/admin/finance/refunds` | Refund 列表 | Current | 14 |
| `/admin/finance/refunds/:id` | Refund 详情与重试 | Current | 14 |
| `/admin/categories` | 分类树管理 | Partial | 15 |
| `/admin/audit` | 审计日志 | Partial | 15 |

当前没有可实现的独立 `/admin/payments` 账本页。

## 全局系统状态

- 401 未登录与 Session expired。
- 403 无权限、账号停用、资质不满足。
- 404 路由、Listing、Worker 或资源不存在。
- 409 Listing 已过期、状态冲突、重复响应或并发操作冲突。
- 500、离线、加载失败与重试。
- 图片上传格式、8 MB 上限、数量上限、失败重试及私有文件鉴权。
- 桌面侧栏、移动底栏、移动抽屉与关键确认对话框。

## 明确不创建的路由

由于当前产品和接口没有相应能力，不设计 `/forgot-password`、`/reset-password`、`/messages`、`/favorites`、`/notifications`、`/admin/payments`，也不设计可用时间日历或直接雇佣流程。
