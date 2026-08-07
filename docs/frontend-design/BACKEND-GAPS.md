# 前端实现前的后端缺口

设计稿会把缺口直接标为 `API GAP`，避免前端用不可靠的全量拉取、猜测状态或虚构字段来填补。以下项目不影响设计审阅，但会影响后续实现完整度。

## P0：进入完整前端实现前应解决

1. **当前服务者资料读取**
   缺少 `GET /api/me/worker-profile`。资料编辑页刷新后无法可靠加载当前用户资料。

2. **Ticket 详情中的当前用户响应状态**
   Ticket detail 不返回 viewer application；`/me/applications` 也不能按 `ticketId` 查询。详情页无法可靠恢复 Pending / Accepted / Rejected / Withdrawn CTA。

3. **Outgoing Application 的类型与成交跳转**
   `/me/applications` 不能按 `ticketKind` 过滤，接受后列表也不返回 `engagementId`。两个工作区无法稳定拆分页数，刷新后也无法直接进入已生成的 Engagement。

4. **付款刷新恢复**
   只有付款配置和付款动作，没有 Payment GET / summary。Stripe 的 PENDING、FAILED 或已成功状态在刷新后不能独立恢复。

5. **是否已经评价**
   没有查询“当前参与者是否已评价该 Engagement”的字段或接口。一次性评价表单刷新后可能再次出现。

6. **管理员争议队列**
   有已知 Engagement 的裁决能力，但没有按 `DISPUTED` 聚合的管理员列表接口。

7. **币种契约**
   Ticket 接受任意三位 ISO currency，而 Settlement / Batch / Refund DTO 不返回 currency，并可能聚合不同币种。应正式限制为 USD，或让财务 DTO 返回 currency 并按币种分组。

## P1：影响体验或运营效率

1. **服务者公开 OFFER 查询**：没有按 `workerId` 查询公开 OFFER 的接口；公开资料只能条件式展示 recent work，不能保证存在可响应的 OPEN OFFER。
2. **账号资料编辑**：当前账号仅能读取摘要；显示名、头像、密码修改与找回密码均无接口。
3. **Inactive 分类恢复**：公共与管理员分类读取都只返回 active，停用后刷新将无法找到并重新启用；父分类停用还会让子树一起消失。
4. **Finance 过滤与详情**：Settlement / Refund 缺少更完整筛选；Batch 缺失败原因、失败项详情和精确重试契约。
5. **审计查询**：当前审计只支持精确 `entityType + entityId`，不能直接支撑全局运营审计流。
6. **Settlement 汇总**：summary 未纳入 `PROCESSING`，需要明确统计口径。
7. **支付账本**：没有 Payment 列表或详情接口，因此不设计后台支付账本页。
8. **管理员总览聚合**：部分 KPI 需要新增聚合接口，不能在浏览器中无界拉取所有分页数据。
9. **Audit OpenAPI actorType**：运行时会返回 `CLIENT` 与 `WORKER`，但当前 Schema 只声明 `USER / ADMIN / SYSTEM`。应修正 OpenAPI 枚举，避免生成式前端类型拒绝真实响应。

## 法务与支付边界

- Terms 与 Privacy 目前只有布局和目录占位，必须经过正式法律审阅并替换内容。
- 注册页条款勾选目前未在后端保存 consent、版本或时间；若它代表法律同意，需要可审计记录。
- 当前支付模型是 provider-dependent 的状态流程与内部 ledger，不应描述为受监管 escrow。
- 当前没有 Stripe Connect 或真实 Worker payout；Settlement 页面只表示内部结算状态，不表示银行打款已经发生。
- 公共文案应使用“Funding status tracked / visible”一类中性措辞，避免“安全托管、保证保护、自动释放”等承诺。

## 状态机实现约束

- `DELIVERED` 可直接批准为 `COMPLETED`，也可进入 `DISPUTED`。
- `DISPUTED` 经管理员裁定后可能回到 `COMPLETED`，也可能进入 `REFUNDED`。
- `ACCEPTED / FUNDED / IN_PROGRESS` 可取消为终态 `CANCELLED`。已付款取消后的退款进度保存在 `refundSummary`，即使退款成功，Engagement 仍保持 `CANCELLED`，不能把它改写成 `REFUNDED`。
- 当前 Stripe 集成只允许 test mode；设计中的 Stripe 分支不是 production 支付上线声明。

## 不应由前端自行补出的能力

聊天、通知、收藏、可用时间日历、可选择时段、直接雇佣、Listing 删除/复制/重开、独立 Admin note 持久化均不在当前契约中。若产品决定加入，应先定义后端模型与权限，再更新设计。
