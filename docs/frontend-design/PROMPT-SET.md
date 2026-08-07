# 设计图生成记录

## 工具模式

- 工具：Codex 内置 `image_gen`。
- 模式：新建高保真位图审阅板 + 基于本地参考图的局部编辑。
- 输出：19 张 1536 × 1024 PNG，归档于 `review-v1/`。
- 参考：已确认的视觉基调、项目现有 seed 图片、前一张基础组件板，以及各业务域上一轮审阅板。
- 未使用网页截图拼贴，也未生成前端 HTML/CSS/组件代码。

## 主提示词集合

所有页面共享以下约束：

> Service Marketplace 的高保真产品设计审阅板。温暖米白画布、深墨色文字、常青绿主操作；OFFER 为蓝色，REQUEST 为紫色；细边框、克制阴影、紧凑但有呼吸感的信息密度；Inter 风格界面字体与少量编辑性衬线标题；自然本地服务照片；无渐变、玻璃拟态和霓虹。桌面优先，并展示关键移动端状态。

业务语义提示词：

> 两边市场而非日历预约。REQUEST 由客户发布、服务者提案、客户作者接受；OFFER 由服务者发布、客户响应、服务者作者接受。一个账号在 Client / Worker 工作区之间切换。不得加入聊天、通知、收藏、可用时间日历、时段选择、直接雇佣或真实 payout UI。

付款与法务提示词：

> 仅表述平台跟踪 Funding / Refund / Settlement 状态。不得声称受监管托管、资金安全保管、自动打款或保证性保护。Mock 付款立即返回 FUNDED；Stripe 使用 Elements、PENDING、Webhook、FUNDED / FAILED 分支。Terms 与 Privacy 只做布局占位并明确需要法律审阅。

## 专项校正

- 移除了无接口支持的收藏、通知、Message、技能 chips、列表时长和 Worker 照片头像。
- 将上传上限统一为 8 MB。
- 修正 OFFER / REQUEST 的响应方向与作者接受规则。
- 将 Listing 第一步设为本地进度，完成价格与地点后才创建 DRAFT。
- 加入 CLOSED 与 EXPIRED 的不同错误表现、资质门槛与响应重复限制。
- 将 Engagement 重画为分支状态机，并单独补齐角色动作矩阵；取消保持 `CANCELLED`，争议才可能裁定为 `COMPLETED` 或 `REFUNDED`。
- 补齐客户 FUNDED / IN_PROGRESS、双向评价、Mock / Stripe、取消与异步退款组合。
- 将财务总览明确标记为 USD 假设，并在设计中标出刷新恢复和聚合 API gap。
- 为每日 Settlement batch 增加数量/金额确认与防重复提交状态，为争议退款增加异步结果分支。
- 对资质、争议证据和审计只展示后端实际返回的元数据。

设计图用于方向审阅，页面文案与示例数据仍可在实现前统一做最终 content pass。
