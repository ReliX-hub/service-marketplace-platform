# Service Marketplace 前端设计审阅包（V1）

这套审阅包覆盖当前后端能力对应的全部前端页面、核心状态和移动端关键路径。当前交付物只有设计图与实现边界说明，**尚未编写任何前端代码**；待设计审阅通过后再进入实现。

## 设计基调

- 温暖的米白画布、深墨色文字、常青绿主操作。
- `OFFER` 使用蓝色，表示服务者发布的服务；`REQUEST` 使用紫色，表示客户发布的任务。
- 信息密度接近成熟 marketplace 与运营后台，避免渐变、玻璃拟态、霓虹和过度装饰。
- 正文与界面采用 Inter 风格，编辑性大标题少量使用 Playfair 风格。
- 桌面优先，同时为发现、筛选、履约、资质与评价保留关键移动端状态。

## 产品规则（审阅时请以此为准）

- 一个普通账号同时拥有 Client 与 Worker 两个工作区；这是工作区切换，不是账号切换。
- `REQUEST`：客户发布任务，服务者提交 Proposal，客户作者接受。
- `OFFER`：服务者发布服务，客户提交 Service request，服务者作者接受。
- 这是提案式服务市场，不是可用时间日历或时段预约产品。
- 当前范围不包含聊天、通知、收藏、站外直聘、可选时间槽和真实服务者打款界面。
- 付款文案只表达平台记录资金状态；不承诺受监管托管、自动打款或保证性保护。
- 视觉稿暂按 Chicago / USD 呈现。USD 仍是产品假设，需在后端正式限制币种，或让财务接口返回并按币种分组。

## 19 张审阅板

| 编号 | 覆盖内容 | 设计图 |
| --- | --- | --- |
| 00 | 设计系统、组件、状态、桌面与移动壳层 | [查看](review-v1/00-ui-foundations.png) |
| 01 | 首页、Marketplace、分类落地页、移动筛选 | [查看](review-v1/01-public-marketplace.png) |
| 02 | OFFER / REQUEST 详情、响应抽屉、资质门槛、关闭与过期 | [查看](review-v1/02-ticket-detail-response.png) |
| 03 | 服务者发现、公开资料、404 与条件式服务入口 | [查看](review-v1/03-worker-discovery-profile.png) |
| 04 | 登录、注册、账号页、认证边界 | [查看](review-v1/04-authentication-account.png) |
| 05 | REQUEST / OFFER 四步发布器、草稿、编辑锁定、资质门槛 | [查看](review-v1/05-listing-composer.png) |
| 06 | Client / Worker 双工作台与移动导航 | [查看](review-v1/06-client-worker-workspaces.png) |
| 07 | Listing 列表、详情、状态矩阵、可编辑与锁定字段 | [查看](review-v1/07-listing-management.png) |
| 08 | 收到与发出的 Application / Proposal、接受与拒绝 | [查看](review-v1/08-applications-proposals.png) |
| 09 | 客户履约、付款、争议、取消与退款状态组合 | [查看](review-v1/09-client-engagement-payment.png) |
| 10 | 服务者履约、开工、证据、交付、取消与评价客户 | [查看](review-v1/10-worker-engagement-delivery.png) |
| 11 | 服务者资料、资质列表、新增、驳回重提与过期状态 | [查看](review-v1/11-worker-profile-credentials.png) |
| 12 | 收入、Settlement、退款与双向声誉 | [查看](review-v1/12-money-reputation.png) |
| 13 | 管理员资质审核、争议裁决与证据查看 | [查看](review-v1/13-admin-casework.png) |
| 14 | 管理员财务总览、Settlement、Batch、Refund | [查看](review-v1/14-admin-finance.png) |
| 15 | 管理员总览、分类树与审计日志 | [查看](review-v1/15-admin-overview-categories-audit.png) |
| 16 | 401 / 403 / 404 / 409 / 500、上传、空状态与响应式规则 | [查看](review-v1/16-system-states-responsive.png) |
| 17 | How it works、Terms、Privacy 与公共页脚 | [查看](review-v1/17-how-it-works-legal.png) |
| 18 | 完整履约状态机、角色动作、评价与 Mock / Stripe 分支 | [查看](review-v1/18-engagement-state-review-payment.png) |

## 推荐审阅顺序

1. 先看 00、01、06，确认品牌、发现体验和双工作区结构。
2. 再看 02、05、07、08，确认两种 Listing 的发布与撮合方向。
3. 重点看 18，再结合 09、10、12，确认付款到履约、争议、退款和评价。
4. 看 11、13–16，确认资质与后台运营。
5. 最后看 17；Terms 与 Privacy 当前仅是版式占位，不能直接上线。

## 设计状态说明

- **Current**：现有后端已能直接支持。
- **Partial**：可展示，但聚合、筛选或刷新恢复能力有限。
- **API GAP**：目标体验已画出，进入前端实现前必须补后端契约，详见 [BACKEND-GAPS.md](BACKEND-GAPS.md)。

页面与路由的完整对应关系见 [PAGE-INVENTORY.md](PAGE-INVENTORY.md)，图像生成方式与提示词集合见 [PROMPT-SET.md](PROMPT-SET.md)。

## 如何给反馈

请按“**板号 + 区域 + 修改意见**”回复，例如：

> 09-C：退款状态太复杂，希望默认折叠时间线。
> 01-A：首页主标题保留，但想换成更偏专业服务的图片。

收到审阅意见后，我会先更新这一套设计图；只有你明确确认设计后，才开始设计和实现前端代码。
