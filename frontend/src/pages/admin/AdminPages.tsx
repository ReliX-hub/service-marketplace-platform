import {
  Check,
  ChevronRight,
  CircleDollarSign,
  FileSearch,
  Layers3,
  Play,
  Plus,
  RefreshCw,
  Search,
  ShieldAlert,
  ShieldCheck,
  Tags,
  X,
} from "lucide-react";
import { useMemo, useState, type FormEvent } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { Alert, Badge, Button, Card, DemoNotice, EmptyState, ErrorState, LinkButton, LoadingState, Modal, PageTitle, Pagination, StatCard } from "../../components/ui";
import { asPage, demoCategories, demoCredentials, demoEngagements, demoRefunds, demoSettlements } from "../../data/demo";
import { useAsyncData } from "../../hooks/useAsyncData";
import { ApiError, api, authenticatedBlobUrl } from "../../lib/api";
import { date, dateTime, money, titleCase } from "../../lib/format";
import type { AuditLog, Category, Credential, CredentialStatus, Engagement, FinancialStatus, Refund, Settlement, SettlementBatch, SettlementSummary } from "../../types";

const demoBatches: SettlementBatch[] = [
  { id: 1, batchId: "SETTLE-DEMO-001", status: "COMPLETED", totalCount: 2, successCount: 2, failedCount: 0, totalAmount: "301.50", startedAt: "2026-08-05T09:00:00Z", completedAt: "2026-08-05T09:00:03Z", createdAt: "2026-08-05T09:00:00Z" },
];

const demoAudit: AuditLog[] = [
  { id: 1, entityType: "ENGAGEMENT", entityId: 1006, action: "ENGAGEMENT_DISPUTED", actorType: "USER", actorId: 2, details: "{\"reason\":\"The agreed rooms were not all completed.\"}", createdAt: "2026-08-05T18:00:00Z" },
  { id: 2, entityType: "CREDENTIAL", entityId: 503, action: "CREDENTIAL_REJECTED", actorType: "ADMIN", actorId: 1, details: "{\"reason\":\"Document is blurry and unreadable.\"}", createdAt: "2026-08-05T18:20:00Z" },
];

function localSummary(items: Settlement[]): SettlementSummary {
  return {
    totalEarnings: String(items.reduce((sum, item) => sum + Number(item.workerPayout), 0)),
    completedAmount: String(items.filter((item) => item.status === "COMPLETED").reduce((sum, item) => sum + Number(item.workerPayout), 0)),
    pendingAmount: String(items.filter((item) => ["PENDING", "PROCESSING"].includes(item.status)).reduce((sum, item) => sum + Number(item.workerPayout), 0)),
    totalCount: items.length,
    completedCount: items.filter((item) => item.status === "COMPLETED").length,
    pendingCount: items.filter((item) => ["PENDING", "PROCESSING"].includes(item.status)).length,
    failedCount: items.filter((item) => item.status === "FAILED").length,
  };
}

export function AdminOverviewPage() {
  const fallback = { credentials: asPage(demoCredentials), settlements: asPage(demoSettlements), refunds: asPage(demoRefunds), summary: localSummary(demoSettlements), batches: asPage(demoBatches) };
  const resource = useAsyncData(async () => {
    const [credentials, settlements, refunds, summary, batches] = await Promise.all([
      api.admin.credentials(undefined, 0, 100), api.admin.settlements(undefined, 0, 100), api.admin.refunds(undefined, 0, 100), api.settlements.summary(), api.admin.batches(0, 5),
    ]);
    return { credentials, settlements, refunds, summary, batches };
  }, fallback, []);

  if (resource.loading) return <LoadingState label="Loading operations overview" />;
  if (resource.error && !resource.demo) return <ErrorState message={resource.error.message} retry={() => void resource.reload()} />;
  const pendingCredentials = resource.data.credentials.items.filter((item) => item.status === "PENDING");
  const failedFinance = resource.data.settlements.items.filter((item) => item.status === "FAILED").length + resource.data.refunds.items.filter((item) => item.status === "FAILED").length;
  return <div>
    {resource.demo && <DemoNotice />}
    <PageTitle eyebrow="Admin operations" title="Marketplace oversight" description="Review credentials, resolve disputes, and monitor settlement and refund health." />
    <div className="stats-grid"><StatCard label="Pending credentials" value={pendingCredentials.length} tone="violet" hint="Requires review" /><StatCard label="Pending payouts" value={money(resource.data.summary.pendingAmount)} tone="blue" hint={`${resource.data.summary.pendingCount} settlements`} /><StatCard label="Finance failures" value={failedFinance} hint="Settlement + refund failures" /><StatCard label="Last batch" value={resource.data.batches.items[0]?.status ? titleCase(resource.data.batches.items[0].status) : "None"} tone="green" hint={resource.data.batches.items[0]?.batchId || "No batch runs"} /></div>
    <div className="content-grid">
      <Card className="panel"><div className="split"><h2>Review queue</h2><Link className="text-link" to="/admin/credentials">View all <ChevronRight size={14} /></Link></div>{pendingCredentials.length === 0 ? <EmptyState title="Queue is clear" description="No credentials currently require review." /> : <div className="stack">{pendingCredentials.slice(0, 5).map((item) => <Link className="list-row" to={`/admin/credentials?credential=${item.id}`} key={item.id}><div className="list-row-main"><strong>{item.workerDisplayName}</strong><span>{titleCase(item.type)} · submitted {date(item.createdAt)}</span></div><Badge value={item.status} /></Link>)}</div>}</Card>
      <Card className="panel"><h2>Operational shortcuts</h2><div className="stack"><Link className="list-row" to="/admin/disputes"><div className="list-row-main"><strong>Dispute lookup</strong><span>Resolve a disputed engagement by ID.</span></div><ShieldAlert size={18} /></Link><Link className="list-row" to="/admin/finance"><div className="list-row-main"><strong>Finance ledger</strong><span>Inspect settlements and payout totals.</span></div><CircleDollarSign size={18} /></Link><Link className="list-row" to="/admin/categories"><div className="list-row-main"><strong>Category catalog</strong><span>Maintain taxonomy and credential gates.</span></div><Tags size={18} /></Link></div></Card>
    </div>
  </div>;
}

export function AdminCredentialsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const raw = searchParams.get("status") || "PENDING";
  const statuses: CredentialStatus[] = ["PENDING", "VERIFIED", "REJECTED", "EXPIRED"];
  const status = statuses.includes(raw as CredentialStatus) ? raw as CredentialStatus : "PENDING";
  const page = Math.max(0, Number(searchParams.get("page") || 0));
  const fallback = asPage(demoCredentials.filter((item) => item.status === status), page, 20);
  const resource = useAsyncData(() => api.admin.credentials(status, page, 20), fallback, [status, page]);
  const [selected, setSelected] = useState<Credential | null>(null);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  const [documentUrl, setDocumentUrl] = useState<string | null>(null);

  const openCredential = async (item: Credential) => {
    setSelected(item); setProblem(null); setDocumentUrl(null);
    if (item.document?.url) {
      try { setDocumentUrl(item.document.managed ? await authenticatedBlobUrl(item.document.url) : item.document.url); }
      catch { setProblem("The private document could not be loaded. You can still review the metadata."); }
    }
  };
  const decide = async (decision: "verify" | "reject") => {
    if (!selected) return;
    if (decision === "reject" && !reason.trim()) { setProblem("A rejection reason is required."); return; }
    setBusy(true); setProblem(null);
    try {
      const updated = decision === "verify" ? await api.admin.verifyCredential(selected.id) : await api.admin.rejectCredential(selected.id, reason.trim());
      resource.setData((current) => ({ ...current, items: current.items.filter((item) => item.id !== updated.id) }));
      setSelected(null); setReason("");
    } catch (caught) {
      if (resource.demo) { resource.setData((current) => ({ ...current, items: current.items.filter((item) => item.id !== selected.id) })); setSelected(null); }
      else setProblem(caught instanceof Error ? caught.message : "Review failed");
    } finally { setBusy(false); }
  };

  return <div>{resource.demo && <DemoNotice />}<PageTitle eyebrow="Trust & safety" title="Credential review" description="Verify worker evidence or return a clear reason for resubmission." />
    <div className="tabs">{statuses.map((value) => <button key={value} className={status === value ? "active" : ""} onClick={() => setSearchParams({ status: value })}>{titleCase(value)}</button>)}</div>
    {resource.loading ? <LoadingState /> : resource.error && !resource.demo ? <ErrorState message={resource.error.message} retry={() => void resource.reload()} /> : resource.data.items.length === 0 ? <Card><EmptyState title="Nothing in this queue" description={`No ${titleCase(status).toLowerCase()} credentials found.`} /></Card> : <Card className="data-card"><div className="table-wrap"><table className="data-table"><thead><tr><th>Worker</th><th>Credential</th><th>Number</th><th>Expires</th><th>Status</th><th /></tr></thead><tbody>{resource.data.items.map((item) => <tr key={item.id}><td><span className="table-primary">{item.workerDisplayName}</span><span className="table-secondary">Worker #{item.workerId}</span></td><td>{titleCase(item.type)}</td><td>{item.credentialNumber || "—"}</td><td>{date(item.expiresAt)}</td><td><Badge value={item.status} /></td><td><Button variant="quiet" onClick={() => void openCredential(item)}>Review</Button></td></tr>)}</tbody></table></div><Pagination page={resource.data.page} totalPages={resource.data.totalPages} onChange={(next) => setSearchParams({ status, page: String(next) })} /></Card>}
    <Modal title={selected ? `${titleCase(selected.type)} · ${selected.workerDisplayName}` : "Credential review"} open={Boolean(selected)} onClose={() => setSelected(null)} footer={selected?.status === "PENDING" ? <><Button variant="quiet" onClick={() => setSelected(null)}>Close</Button><Button variant="danger" busy={busy} onClick={() => void decide("reject")}><X size={15} /> Reject</Button><Button busy={busy} onClick={() => void decide("verify")}><Check size={15} /> Verify</Button></> : <Button onClick={() => setSelected(null)}>Close</Button>}>
      {problem && <Alert tone="danger">{problem}</Alert>}{selected && <div className="stack">{documentUrl ? <img className="credential-document" src={documentUrl} alt="Private credential document" /> : <Alert tone="warning">No reviewable document is attached.</Alert>}<div className="form-grid"><div className="field"><span className="field-label">Number</span><strong>{selected.credentialNumber || "—"}</strong></div><div className="field"><span className="field-label">Validity</span><strong>{date(selected.issuedAt)} – {date(selected.expiresAt)}</strong></div></div>{selected.status === "PENDING" && <div className="field"><label>Rejection reason</label><textarea maxLength={500} value={reason} onChange={(event) => setReason(event.target.value)} placeholder="Required only when rejecting" /><small>{reason.length} / 500</small></div>}</div>}
    </Modal>
  </div>;
}

export function AdminDisputesPage() {
  const navigate = useNavigate();
  const [id, setId] = useState("");
  const demoDisputes = demoEngagements.filter((item) => item.status === "DISPUTED");
  const submit = (event: FormEvent) => { event.preventDefault(); if (/^\d+$/.test(id)) navigate(`/admin/disputes/${id}`); };
  return <div><PageTitle eyebrow="Trust & safety" title="Dispute resolution" description="Inspect evidence and choose one terminal outcome: complete the engagement or refund the client." />
    <Alert tone="warning" title="Current API boundary">The backend supports dispute resolution by engagement ID, but it does not provide an admin dispute-list endpoint. Use the exact engagement ID from an escalation or audit event.</Alert>
    <Card className="panel lookup-card"><h2>Open a disputed engagement</h2><form className="lookup-form" onSubmit={submit}><div className="field"><label>Engagement ID</label><input inputMode="numeric" value={id} onChange={(event) => setId(event.target.value.replace(/\D/g, ""))} placeholder="e.g. 1006" /></div><Button type="submit" disabled={!id}><Search size={16} /> Open case</Button></form></Card>
    {demoDisputes.length > 0 && <Card className="panel"><div className="split"><h2>Offline demo case</h2><Badge value="DEMO" /></div>{demoDisputes.map((item) => <Link className="list-row" to={`/admin/disputes/${item.id}`} key={item.id}><div className="list-row-main"><strong>ENG-{item.id}</strong><span>{item.clientName} · {item.workerDisplayName}</span></div><Badge value={item.status} /></Link>)}</Card>}
  </div>;
}

export function AdminDisputeDetailPage() {
  const { engagementId = "" } = useParams();
  const navigate = useNavigate();
  const fallback = demoEngagements.find((item) => String(item.id) === engagementId) ?? demoEngagements.find((item) => item.status === "DISPUTED")!;
  const resource = useAsyncData(async () => ({ engagement: await api.engagements.get(engagementId), evidence: await api.engagements.deliverables(engagementId) }), { engagement: fallback, evidence: [] }, [engagementId]);
  const [resolution, setResolution] = useState<"COMPLETED" | "REFUNDED">("REFUNDED");
  const [reason, setReason] = useState("");
  const [confirm, setConfirm] = useState(false);
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);

  const resolve = async () => {
    setBusy(true); setProblem(null);
    try { const updated = await api.admin.resolveEngagement(engagementId, resolution, reason.trim() || undefined); resource.setData((current) => ({ ...current, engagement: updated })); setConfirm(false); }
    catch (caught) { if (resource.demo) { resource.setData((current) => ({ ...current, engagement: { ...current.engagement, status: resolution } })); setConfirm(false); } else setProblem(caught instanceof Error ? caught.message : "Resolution failed"); }
    finally { setBusy(false); }
  };
  if (resource.loading) return <LoadingState label="Loading dispute" />;
  if (resource.error && !resource.demo) return <ErrorState message={resource.error.message} retry={() => void resource.reload()} />;
  const item = resource.data.engagement;
  return <div>{resource.demo && <DemoNotice />}<Button variant="quiet" onClick={() => navigate("/admin/disputes")}><ChevronRight style={{ transform: "rotate(180deg)" }} size={15} /> Back</Button><PageTitle eyebrow={`ENG-${item.id}`} title="Dispute case" description={`${item.clientName} and ${item.workerDisplayName} · ${money(item.amount)}`} actions={<Badge value={item.status} />} />{problem && <Alert tone="danger">{problem}</Alert>}
    <div className="content-grid"><div className="stack"><Card className="panel"><h2>Case statement</h2><blockquote className="case-quote">{item.disputeReason || "No dispute reason was recorded."}</blockquote><div className="form-grid"><div className="field"><span className="field-label">Client</span><strong>{item.clientName}</strong></div><div className="field"><span className="field-label">Worker</span><strong>{item.workerDisplayName}</strong></div><div className="field"><span className="field-label">Delivered</span><strong>{dateTime(item.deliveredAt)}</strong></div><div className="field"><span className="field-label">Disputed</span><strong>{dateTime(item.disputedAt)}</strong></div></div></Card><Card className="panel"><h2>Private evidence</h2>{resource.data.evidence.length === 0 ? <EmptyState title="No evidence returned" description="The worker did not attach delivery images, or this is the offline demo case." /> : <div className="photo-grid">{resource.data.evidence.map((evidence) => <img src={evidence.image.thumb} alt={evidence.note || "Evidence"} key={evidence.id} />)}</div>}</Card></div>
      <Card className="panel"><h2>Administrative decision</h2>{item.status !== "DISPUTED" ? <Alert tone="success" title="Case resolved">This engagement is now {titleCase(item.status)} and cannot be resolved again.</Alert> : <div className="stack"><Alert tone="warning">This action is terminal and creates an audit event. REFUNDED also starts the asynchronous refund lifecycle.</Alert><div className="field"><label>Outcome</label><div className="segmented"><button type="button" className={resolution === "REFUNDED" ? "active" : ""} onClick={() => setResolution("REFUNDED")}>Refund client</button><button type="button" className={resolution === "COMPLETED" ? "active" : ""} onClick={() => setResolution("COMPLETED")}>Complete for worker</button></div></div><div className="field"><label>Decision reason</label><textarea maxLength={500} value={reason} onChange={(event) => setReason(event.target.value)} /><small>{reason.length} / 500</small></div><Button variant={resolution === "REFUNDED" ? "danger" : "primary"} onClick={() => setConfirm(true)}>Resolve case</Button></div>}</Card>
    </div><Modal title={`Resolve as ${titleCase(resolution)}?`} open={confirm} onClose={() => setConfirm(false)} footer={<><Button variant="quiet" onClick={() => setConfirm(false)}>Cancel</Button><Button variant={resolution === "REFUNDED" ? "danger" : "primary"} busy={busy} onClick={() => void resolve()}>Confirm terminal decision</Button></>}><p>This cannot be reversed from the current API. Confirm only after reviewing the case statement and evidence.</p></Modal>
  </div>;
}

export function AdminFinancePage() {
  const [status, setStatus] = useState<FinancialStatus | "">("");
  const fallbackAll = demoSettlements;
  const resource = useAsyncData(async () => ({ summary: await api.settlements.summary(), settlements: await api.admin.settlements(undefined, 0, 100) }), { summary: localSummary(fallbackAll), settlements: asPage(fallbackAll, 0, 100) }, []);
  const items = resource.data.settlements.items.filter((item) => !status || item.status === status);
  return <div>{resource.demo && <DemoNotice />}<PageTitle eyebrow="Finance operations" title="Settlements" description="Monitor gross amounts, platform fees, worker payouts, and batch processing." actions={<><LinkButton to="/admin/finance/refunds" variant="quiet">Refunds</LinkButton><LinkButton to="/admin/finance/batches" variant="secondary"><Layers3 size={16} /> Batch history</LinkButton></>} />{resource.loading ? <LoadingState /> : resource.error && !resource.demo ? <ErrorState message={resource.error.message} retry={() => void resource.reload()} /> : <><div className="stats-grid"><StatCard label="Worker earnings" value={money(resource.data.summary.totalEarnings)} tone="green" /><StatCard label="Completed" value={money(resource.data.summary.completedAmount)} tone="blue" /><StatCard label="Pending" value={money(resource.data.summary.pendingAmount)} tone="violet" /><StatCard label="Failed" value={resource.data.summary.failedCount} /></div><div className="tabs">{(["", "PENDING", "PROCESSING", "COMPLETED", "FAILED"] as const).map((value) => <button key={value || "ALL"} className={status === value ? "active" : ""} onClick={() => setStatus(value)}>{value ? titleCase(value) : "All"}</button>)}</div>{items.length === 0 ? <Card><EmptyState title="No settlements" description="Nothing matches this status." /></Card> : <Card className="data-card"><div className="table-wrap"><table className="data-table"><thead><tr><th>Settlement</th><th>Engagement</th><th>Gross</th><th>Fee</th><th>Payout</th><th>Status</th><th /></tr></thead><tbody>{items.map((item) => <tr key={item.id}><td>SET-{item.id}</td><td>ENG-{item.engagementId}</td><td>{money(item.totalAmount)}</td><td>{money(item.platformFee)}</td><td>{money(item.workerPayout)}</td><td><Badge value={item.status} /></td><td><LinkButton variant="quiet" to={`/admin/finance/settlements/${item.id}`}>Details</LinkButton></td></tr>)}</tbody></table></div></Card>}</>}</div>;
}

export function AdminBatchesPage() {
  const resource = useAsyncData(() => api.admin.batches(0, 100), asPage(demoBatches, 0, 100), []);
  const [confirm, setConfirm] = useState(false);
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  const run = async () => { setBusy(true); setProblem(null); try { const created = await api.admin.runBatch() as SettlementBatch; resource.setData((current) => ({ ...current, items: [created, ...current.items] })); setConfirm(false); } catch (caught) { if (resource.demo) { resource.setData((current) => ({ ...current, items: [{ ...demoBatches[0], id: Date.now(), batchId: `SETTLE-DEMO-${Date.now()}` }, ...current.items] })); setConfirm(false); } else setProblem(caught instanceof Error ? caught.message : "Batch run failed"); } finally { setBusy(false); } };
  return <div>{resource.demo && <DemoNotice />}<PageTitle eyebrow="Finance operations" title="Settlement batches" description="Process pending settlements and inspect each immutable batch summary." actions={<Button onClick={() => setConfirm(true)}><Play size={16} /> Run next batch</Button>} />{problem && <Alert tone="danger">{problem}</Alert>}{resource.loading ? <LoadingState /> : resource.error && !resource.demo ? <ErrorState message={resource.error.message} retry={() => void resource.reload()} /> : resource.data.items.length === 0 ? <Card><EmptyState title="No batches yet" description="Run the first batch when pending settlements are ready." /></Card> : <Card className="data-card"><div className="table-wrap"><table className="data-table"><thead><tr><th>Batch</th><th>Started</th><th>Records</th><th>Success</th><th>Failed</th><th>Total</th><th>Status</th></tr></thead><tbody>{resource.data.items.map((item) => <tr key={item.id}><td>{item.batchId}</td><td>{dateTime(item.startedAt)}</td><td>{item.totalCount}</td><td>{item.successCount}</td><td>{item.failedCount}</td><td>{money(item.totalAmount)}</td><td><Badge value={item.status} /></td></tr>)}</tbody></table></div></Card>}<Modal title="Run settlement batch?" open={confirm} onClose={() => setConfirm(false)} footer={<><Button variant="quiet" onClick={() => setConfirm(false)}>Cancel</Button><Button busy={busy} onClick={() => void run()}>Run batch</Button></>}><p>All currently eligible pending settlements will be processed. A permanent batch record will be created.</p></Modal></div>;
}

export function AdminRefundsPage() {
  const [status, setStatus] = useState<FinancialStatus | "">("");
  const resource = useAsyncData(() => api.admin.refunds(undefined, 0, 100), asPage(demoRefunds, 0, 100), []);
  const items = resource.data.items.filter((item) => !status || item.status === status);
  return <div>{resource.demo && <DemoNotice />}<PageTitle eyebrow="Finance operations" title="Refunds" description="Review client refunds and provider failures across the marketplace." />{resource.loading ? <LoadingState /> : resource.error && !resource.demo ? <ErrorState message={resource.error.message} retry={() => void resource.reload()} /> : <><div className="tabs">{(["", "PENDING", "PROCESSING", "COMPLETED", "FAILED"] as const).map((value) => <button key={value || "ALL"} className={status === value ? "active" : ""} onClick={() => setStatus(value)}>{value ? titleCase(value) : "All"}</button>)}</div>{items.length === 0 ? <Card><EmptyState title="No refunds" description="Nothing matches this status." /></Card> : <Card className="data-card"><div className="table-wrap"><table className="data-table"><thead><tr><th>Refund</th><th>Engagement</th><th>Amount</th><th>Reason</th><th>Provider</th><th>Status</th><th /></tr></thead><tbody>{items.map((item) => <tr key={item.id}><td>REF-{item.id}</td><td>ENG-{item.engagementId}</td><td>{money(item.amount)}</td><td>{item.reason}</td><td>{item.providerStatus || "—"}</td><td><Badge value={item.status} /></td><td><LinkButton variant="quiet" to={`/admin/finance/refunds/${item.id}`}>Details</LinkButton></td></tr>)}</tbody></table></div></Card>}</>}</div>;
}

interface CategoryForm { id?: number; code: string; name: string; description: string; icon: string; requiredCredential: "" | "ELECTRICAL_LICENSE" | "DRIVER_LICENSE" | "BACKGROUND_CHECK"; parentId: string; active: boolean }
const emptyCategory: CategoryForm = { code: "", name: "", description: "", icon: "", requiredCredential: "", parentId: "", active: true };

function flattenCategories(items: Category[]): Category[] { return items.flatMap((item) => [item, ...flattenCategories(item.children || [])]); }

export function AdminCategoriesPage() {
  const resource = useAsyncData(() => api.categories.list(), demoCategories, []);
  const [form, setForm] = useState<CategoryForm | null>(null);
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  const items = useMemo(() => flattenCategories(resource.data), [resource.data]);
  const edit = (item: Category) => setForm({ id: item.id, code: item.code, name: item.name, description: item.description || "", icon: item.icon || "", requiredCredential: item.requiredCredential || "", parentId: item.parentId ? String(item.parentId) : "", active: item.active });
  const save = async () => {
    if (!form || !form.code.trim() || !form.name.trim()) { setProblem("Code and name are required."); return; }
    setBusy(true); setProblem(null);
    const body = { code: form.code.trim().toUpperCase(), name: form.name.trim(), description: form.description.trim() || null, icon: form.icon.trim() || null, requiredCredential: form.requiredCredential || null, parentId: form.parentId ? Number(form.parentId) : null, active: form.active };
    try { if (form.id) await api.admin.updateCategory(form.id, body); else await api.admin.createCategory(body); await resource.reload(); setForm(null); }
    catch (caught) { if (resource.demo) { const created: Category = { id: form.id || Date.now(), ...body, children: [], createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() }; resource.setData((current) => form.id ? current.map((item) => item.id === created.id ? created : item) : [...current, created]); setForm(null); } else setProblem(caught instanceof Error ? caught.message : "Unable to save category"); }
    finally { setBusy(false); }
  };
  const toggle = async (item: Category) => { try { await api.admin.setCategoryActive(item.id, !item.active); await resource.reload(); } catch (caught) { if (resource.demo) resource.setData((current) => current.map((value) => value.id === item.id ? { ...value, active: !value.active } : value)); else setProblem(caught instanceof Error ? caught.message : "Unable to update category"); } };
  return <div>{resource.demo && <DemoNotice />}<PageTitle eyebrow="Catalog operations" title="Categories" description="Maintain discovery taxonomy and credential requirements." actions={<Button onClick={() => setForm(emptyCategory)}><Plus size={16} /> New category</Button>} />{problem && <Alert tone="danger">{problem}</Alert>}{resource.loading ? <LoadingState /> : resource.error && !resource.demo ? <ErrorState message={resource.error.message} retry={() => void resource.reload()} /> : <Card className="data-card"><div className="table-wrap"><table className="data-table"><thead><tr><th>Category</th><th>Code</th><th>Parent</th><th>Credential gate</th><th>Status</th><th /></tr></thead><tbody>{items.map((item) => <tr key={item.id}><td><span className="table-primary">{item.name}</span><span className="table-secondary">{item.description || "No description"}</span></td><td>{item.code}</td><td>{items.find((value) => value.id === item.parentId)?.name || "Root"}</td><td>{item.requiredCredential ? titleCase(item.requiredCredential) : "None"}</td><td><Badge value={item.active ? "ACTIVE" : "INACTIVE"} /></td><td><div className="table-actions"><Button variant="quiet" onClick={() => edit(item)}>Edit</Button><Button variant={item.active ? "danger" : "secondary"} onClick={() => void toggle(item)}>{item.active ? "Deactivate" : "Activate"}</Button></div></td></tr>)}</tbody></table></div></Card>}
    <Modal title={form?.id ? "Edit category" : "Create category"} open={Boolean(form)} onClose={() => setForm(null)} footer={<><Button variant="quiet" onClick={() => setForm(null)}>Cancel</Button><Button busy={busy} onClick={() => void save()}>Save category</Button></>}>{form && <div className="stack"><div className="form-grid"><div className="field"><label>Code *</label><input maxLength={50} value={form.code} onChange={(event) => setForm({ ...form, code: event.target.value.replace(/[^A-Za-z0-9_]/g, "") })} /></div><div className="field"><label>Name *</label><input maxLength={100} value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} /></div></div><div className="field"><label>Description</label><textarea value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })} /></div><div className="form-grid"><div className="field"><label>Icon name</label><input maxLength={100} value={form.icon} onChange={(event) => setForm({ ...form, icon: event.target.value })} /></div><div className="field"><label>Parent</label><select value={form.parentId} onChange={(event) => setForm({ ...form, parentId: event.target.value })}><option value="">Root category</option>{items.filter((item) => item.id !== form.id).map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></div></div><div className="field"><label>Required credential</label><select value={form.requiredCredential} onChange={(event) => setForm({ ...form, requiredCredential: event.target.value as CategoryForm["requiredCredential"] })}><option value="">None</option><option value="ELECTRICAL_LICENSE">Electrical license</option><option value="DRIVER_LICENSE">Driver license</option><option value="BACKGROUND_CHECK">Background check</option></select></div><label className="check-row"><input type="checkbox" checked={form.active} onChange={(event) => setForm({ ...form, active: event.target.checked })} /> Active and publicly discoverable</label></div>}</Modal>
  </div>;
}

export function AdminAuditPage() {
  const [entityType, setEntityType] = useState("ENGAGEMENT");
  const [entityId, setEntityId] = useState("1006");
  const [query, setQuery] = useState<{ type: string; id: string } | null>(null);
  const resource = useAsyncData(() => query ? api.admin.audit(query.type, query.id, 0, 100) : Promise.resolve(asPage<AuditLog>([])), query ? asPage(demoAudit.filter((item) => item.entityType === query.type && String(item.entityId) === query.id)) : asPage<AuditLog>([]), [query?.type, query?.id]);
  const submit = (event: FormEvent) => { event.preventDefault(); if (entityType.trim() && /^\d+$/.test(entityId)) setQuery({ type: entityType.trim().toUpperCase(), id: entityId }); };
  return <div>{resource.demo && query && <DemoNotice />}<PageTitle eyebrow="Operations evidence" title="Audit lookup" description="Query immutable business events by exact entity type and ID." /><Alert tone="info">Audit lookup intentionally requires an entity pair; the API does not expose a global activity feed.</Alert><Card className="panel lookup-card"><form className="lookup-form" onSubmit={submit}><div className="field"><label>Entity type</label><select value={entityType} onChange={(event) => setEntityType(event.target.value)}><option>ENGAGEMENT</option><option>CREDENTIAL</option><option>SETTLEMENT</option><option>REFUND</option><option>TICKET</option><option>APPLICATION</option></select></div><div className="field"><label>Entity ID</label><input inputMode="numeric" value={entityId} onChange={(event) => setEntityId(event.target.value.replace(/\D/g, ""))} /></div><Button type="submit"><FileSearch size={16} /> Search logs</Button></form></Card>{query && (resource.loading ? <LoadingState /> : resource.error && !resource.demo ? <ErrorState message={resource.error.message} retry={() => void resource.reload()} /> : resource.data.items.length === 0 ? <Card><EmptyState title="No audit events" description="No events were found for this entity pair." /></Card> : <Card className="data-card"><div className="table-wrap"><table className="data-table"><thead><tr><th>Time</th><th>Action</th><th>Actor</th><th>Entity</th><th>Details</th></tr></thead><tbody>{resource.data.items.map((item) => <tr key={item.id}><td>{dateTime(item.createdAt)}</td><td><span className="table-primary">{titleCase(item.action)}</span></td><td>{item.actorType}{item.actorId ? ` #${item.actorId}` : ""}</td><td>{item.entityType} #{item.entityId}</td><td><code className="audit-details">{item.details || "{}"}</code></td></tr>)}</tbody></table></div></Card>)}</div>;
}
