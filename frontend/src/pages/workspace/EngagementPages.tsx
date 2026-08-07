import {
  ArrowLeft,
  Camera,
  Check,
  CircleDollarSign,
  Clock3,
  FileCheck2,
  Flag,
  Play,
  RefreshCw,
  Star,
  Trash2,
  Upload,
  WalletCards,
  X,
} from "lucide-react";
import { useMemo, useState, type FormEvent } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { Elements, PaymentElement, useElements, useStripe } from "@stripe/react-stripe-js";
import { loadStripe } from "@stripe/stripe-js";
import { useAuth } from "../../auth/AuthContext";
import { Alert, Avatar, Badge, Button, Card, DemoNotice, EmptyState, ErrorState, LinkButton, LoadingState, Modal, PageTitle, Pagination, Rating, StatCard } from "../../components/ui";
import { asPage, demoEngagements, demoRefunds, demoReviews, demoSettlements } from "../../data/demo";
import { useAsyncData } from "../../hooks/useAsyncData";
import { ApiError, api, assetUrl } from "../../lib/api";
import { date, dateTime, money, titleCase } from "../../lib/format";
import type { Deliverable, Engagement, EngagementStatus, FinancialStatus, Payment, Refund, Review, Settlement, SettlementSummary } from "../../types";

type Workspace = "client" | "worker";

const terminalStatuses: EngagementStatus[] = ["COMPLETED", "CANCELLED", "REFUNDED"];
const demoDeliverables: Deliverable[] = [
  { id: 1, image: { thumb: "/images/plumbing-leak.jpg", large: "/images/plumbing-leak.jpg" }, position: 0, note: "Completed repair and leak test.", submittedBy: 10, createdAt: "2026-08-05T18:00:00Z" },
  { id: 2, image: { thumb: "/images/tech-support.jpg", large: "/images/tech-support.jpg" }, position: 1, note: "Final setup and coverage check.", submittedBy: 10, createdAt: "2026-08-05T18:05:00Z" },
];

function engagementFallback(id: string, workspace: Workspace) {
  const found = demoEngagements.find((item) => String(item.id) === id);
  if (found) return found;
  return demoEngagements.find((item) => workspace === "client" ? item.clientId === 2 : item.workerUserId === 10) ?? demoEngagements[0];
}

export function EngagementListPage({ workspace }: { workspace: Workspace }) {
  const [searchParams, setSearchParams] = useSearchParams();
  const page = Math.max(0, Number(searchParams.get("page") || 0));
  const rawStatus = searchParams.get("status") || "";
  const allowed: EngagementStatus[] = ["ACCEPTED", "FUNDED", "IN_PROGRESS", "DELIVERED", "COMPLETED", "DISPUTED", "CANCELLED", "REFUNDED"];
  const status = allowed.includes(rawStatus as EngagementStatus) ? rawStatus as EngagementStatus : "";
  const fallbackItems = demoEngagements.filter((item) => !status || item.status === status);
  const resource = useAsyncData(
    () => api.engagements.list(workspace, status || undefined, page, 20),
    asPage(fallbackItems, page, 20),
    [workspace, status, page],
  );

  return (
    <div>
      {resource.demo && <DemoNotice />}
      <PageTitle eyebrow={`${workspace} workspace`} title="Engagements" description="Follow accepted work from funding through delivery, resolution, and review." />
      <div className="tabs">
        {["", "ACCEPTED", "FUNDED", "IN_PROGRESS", "DELIVERED", "DISPUTED", "COMPLETED"].map((value) => (
          <button key={value || "ALL"} className={status === value ? "active" : ""} onClick={() => setSearchParams(value ? { status: value } : {})}>{value ? titleCase(value) : "All"}</button>
        ))}
      </div>
      {resource.loading ? <LoadingState label="Loading engagements" /> : resource.error && !resource.demo ? (
        <ErrorState message={resource.error.message} retry={() => void resource.reload()} />
      ) : resource.data.items.length === 0 ? (
        <Card><EmptyState title="No engagements found" description="Accepted responses will appear here." action={<LinkButton to="/marketplace">Browse marketplace</LinkButton>} /></Card>
      ) : (
        <Card className="data-card">
          <div className="table-wrap"><table className="data-table">
            <thead><tr><th>Engagement</th><th>{workspace === "client" ? "Worker" : "Client"}</th><th>Amount</th><th>Schedule</th><th>Status</th><th /></tr></thead>
            <tbody>{resource.data.items.map((item) => <tr key={item.id}>
              <td><span className="table-primary">ENG-{item.id}</span><span className="table-secondary">Ticket #{item.ticketId}</span></td>
              <td>{workspace === "client" ? item.workerDisplayName : item.clientName}</td>
              <td>{money(item.amount)}</td>
              <td>{dateTime(item.scheduledStart)}</td>
              <td><Badge value={item.status} /></td>
              <td><LinkButton variant="quiet" to={`/app/${workspace}/engagements/${item.id}`}>Open</LinkButton></td>
            </tr>)}</tbody>
          </table></div>
          <Pagination page={resource.data.page} totalPages={resource.data.totalPages} onChange={(next) => setSearchParams({ ...(status ? { status } : {}), page: String(next) })} />
        </Card>
      )}
    </div>
  );
}

function EngagementTimeline({ engagement }: { engagement: Engagement }) {
  const events = [
    ["Accepted", engagement.acceptedAt],
    ["Funded", engagement.fundedAt],
    ["Work started", engagement.startedAt],
    ["Delivered", engagement.deliveredAt],
    ["Approved", engagement.approvedAt],
    ["Completed", engagement.completedAt],
    ["Disputed", engagement.disputedAt],
    ["Cancelled", engagement.cancelledAt],
  ].filter((item): item is [string, string] => Boolean(item[1]));

  return <div className="timeline">{events.map(([label, at], index) => <div className="timeline-item" key={`${label}-${at}`}><span className={index === events.length - 1 ? "active" : "complete"}>{index < events.length - 1 ? <Check size={13} /> : index + 1}</span><div><strong>{label}</strong><small>{dateTime(at)}</small></div></div>)}</div>;
}

export function EngagementDetailPage({ workspace }: { workspace: Workspace }) {
  const { engagementId = "" } = useParams();
  const navigate = useNavigate();
  const fallback = engagementFallback(engagementId, workspace);
  const resource = useAsyncData(
    async () => {
      const engagement = await api.engagements.get(engagementId);
      const deliverables = engagement.deliverableCount > 0 ? await api.engagements.deliverables(engagementId) : [];
      return { engagement, deliverables };
    },
    { engagement: fallback, deliverables: fallback.deliverableCount ? demoDeliverables.slice(0, fallback.deliverableCount) : [] },
    [engagementId],
  );
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  const [dialog, setDialog] = useState<"cancel" | "dispute" | "review" | "deliverable" | null>(null);
  const [reason, setReason] = useState("");
  const [rating, setRating] = useState(5);
  const [comment, setComment] = useState("");
  const [evidence, setEvidence] = useState<File | null>(null);
  const [evidenceNote, setEvidenceNote] = useState("");
  const engagement = resource.data.engagement;

  const update = async (action: "start" | "deliver" | "approve") => {
    setBusy(true); setProblem(null);
    try {
      const next = action === "start" ? await api.engagements.start(engagement.id) : action === "deliver" ? await api.engagements.deliver(engagement.id) : await api.engagements.approve(engagement.id);
      resource.setData((current) => ({ ...current, engagement: next }));
    } catch (caught) {
      if (resource.demo) {
        const status: EngagementStatus = action === "start" ? "IN_PROGRESS" : action === "deliver" ? "DELIVERED" : "COMPLETED";
        resource.setData((current) => ({ ...current, engagement: { ...current.engagement, status } }));
      } else setProblem(caught instanceof Error ? caught.message : "Unable to update engagement");
    } finally { setBusy(false); }
  };

  const submitDialog = async () => {
    setBusy(true); setProblem(null);
    try {
      if (dialog === "cancel") {
        const next = await api.engagements.cancel(engagement.id, reason.trim() || undefined);
        resource.setData((current) => ({ ...current, engagement: next }));
      } else if (dialog === "dispute") {
        if (!reason.trim()) throw new Error("Please explain what needs review.");
        const next = await api.engagements.dispute(engagement.id, reason.trim());
        resource.setData((current) => ({ ...current, engagement: next }));
      } else if (dialog === "review") {
        await api.engagements.review(engagement.id, { rating, comment: comment.trim() || undefined });
      } else if (dialog === "deliverable") {
        if (!evidence) throw new Error("Choose an image first.");
        if (evidence.size > 8 * 1024 * 1024) throw new Error("Evidence images must be 8 MB or smaller.");
        const created = await api.engagements.addDeliverable(engagement.id, evidence, evidenceNote.trim() || undefined);
        resource.setData((current) => ({ engagement: { ...current.engagement, deliverableCount: current.engagement.deliverableCount + 1 }, deliverables: [...current.deliverables, created] }));
      }
      setDialog(null); setReason(""); setComment(""); setEvidence(null); setEvidenceNote("");
    } catch (caught) {
      if (resource.demo) {
        if (dialog === "cancel") resource.setData((current) => ({ ...current, engagement: { ...current.engagement, status: "CANCELLED", cancellationReason: reason || null } }));
        if (dialog === "dispute") resource.setData((current) => ({ ...current, engagement: { ...current.engagement, status: "DISPUTED", disputeReason: reason } }));
        if (dialog === "deliverable" && evidence) resource.setData((current) => ({ engagement: { ...current.engagement, deliverableCount: current.engagement.deliverableCount + 1 }, deliverables: [...current.deliverables, { id: Date.now(), image: { thumb: URL.createObjectURL(evidence), large: URL.createObjectURL(evidence) }, position: current.deliverables.length, note: evidenceNote || null, submittedBy: engagement.workerUserId, createdAt: new Date().toISOString() }] }));
        setDialog(null);
      } else if (caught instanceof ApiError && caught.status === 409) setProblem("This action was already completed or the engagement changed. Refresh and try again.");
      else setProblem(caught instanceof Error ? caught.message : "Action failed");
    } finally { setBusy(false); }
  };

  const removeEvidence = async (item: Deliverable) => {
    setBusy(true);
    try {
      await api.engagements.deleteDeliverable(engagement.id, item.id);
      resource.setData((current) => ({ engagement: { ...current.engagement, deliverableCount: Math.max(0, current.engagement.deliverableCount - 1) }, deliverables: current.deliverables.filter((value) => value.id !== item.id) }));
    } catch (caught) {
      if (resource.demo) resource.setData((current) => ({ engagement: { ...current.engagement, deliverableCount: Math.max(0, current.engagement.deliverableCount - 1) }, deliverables: current.deliverables.filter((value) => value.id !== item.id) }));
      else setProblem(caught instanceof Error ? caught.message : "Unable to delete evidence");
    } finally { setBusy(false); }
  };

  if (resource.loading) return <LoadingState label="Loading engagement" />;
  if (resource.error && !resource.demo) return <ErrorState message={resource.error.message} retry={() => void resource.reload()} />;

  const canCancel = ["ACCEPTED", "FUNDED", "IN_PROGRESS"].includes(engagement.status);
  return <div>
    {resource.demo && <DemoNotice />}
    <Button variant="quiet" onClick={() => navigate(`/app/${workspace}/engagements`)}><ArrowLeft size={15} /> Back to engagements</Button>
    <PageTitle eyebrow={`ENG-${engagement.id}`} title={`Work with ${workspace === "client" ? engagement.workerDisplayName : engagement.clientName}`} description={`Ticket #${engagement.ticketId} · ${money(engagement.amount)}`} actions={<Badge value={engagement.status} />} />
    {problem && <Alert tone="danger">{problem}</Alert>}
    {engagement.status === "DISPUTED" && <Alert tone="warning" title="Administrative review in progress">{engagement.disputeReason || "A dispute was opened for this engagement."}</Alert>}
    {engagement.refundSummary && <Alert tone={engagement.refundSummary.status === "FAILED" ? "danger" : "info"} title={`Refund ${titleCase(engagement.refundSummary.status)}`}>{money(engagement.refundSummary.amount)} · Updated {dateTime(engagement.refundSummary.updatedAt)}</Alert>}
    <div className="content-grid">
      <div className="stack">
        <Card className="panel">
          <div className="split"><div><span className="eyebrow">Lifecycle</span><h2>Engagement progress</h2></div><Badge value={engagement.status} /></div>
          <EngagementTimeline engagement={engagement} />
        </Card>
        <Card className="panel">
          <div className="split"><div><span className="eyebrow">Private evidence</span><h2>Delivery gallery</h2></div>{workspace === "worker" && engagement.status === "IN_PROGRESS" && resource.data.deliverables.length < 8 && <Button variant="secondary" onClick={() => setDialog("deliverable")}><Camera size={15} /> Add evidence</Button>}</div>
          {resource.data.deliverables.length === 0 ? <EmptyState title="No delivery evidence yet" description={workspace === "worker" ? "Add up to 8 private images while work is in progress." : "The worker can add evidence after starting work."} /> : <div className="photo-grid">{resource.data.deliverables.map((item) => <figure key={item.id} className="evidence-card"><img src={assetUrl(item.image.thumb)} alt={item.note || "Delivery evidence"} /><figcaption>{item.note || "Delivery evidence"}<small>{dateTime(item.createdAt)}</small></figcaption>{workspace === "worker" && engagement.status === "IN_PROGRESS" && <Button aria-label="Delete evidence" variant="danger" disabled={busy} onClick={() => void removeEvidence(item)}><Trash2 size={14} /></Button>}</figure>)}</div>}
        </Card>
      </div>
      <div className="stack">
        <Card className="panel">
          <h2>Work summary</h2>
          <div className="summary-list">
            <div className="summary-item"><CircleDollarSign size={17} /><div><span>Agreed amount</span><strong>{money(engagement.amount)}</strong></div></div>
            <div className="summary-item"><Clock3 size={17} /><div><span>Scheduled</span><strong>{dateTime(engagement.scheduledStart)} – {dateTime(engagement.scheduledEnd)}</strong></div></div>
            <div className="summary-item"><FileCheck2 size={17} /><div><span>Notes</span><strong>{engagement.notes || "No additional notes"}</strong></div></div>
          </div>
          <LinkButton className="button-block" variant="secondary" to={`/tickets/${engagement.ticketId}`}>View original listing</LinkButton>
        </Card>
        <Card className="panel action-panel"><h2>Available actions</h2>
          {workspace === "client" && engagement.status === "ACCEPTED" && <LinkButton className="button-block" to={`/app/client/engagements/${engagement.id}/payment`}><WalletCards size={16} /> Fund engagement</LinkButton>}
          {workspace === "worker" && engagement.status === "ACCEPTED" && <Alert tone="info">The client must fund this engagement before work can begin.</Alert>}
          {workspace === "worker" && engagement.status === "FUNDED" && <Button className="button-block" busy={busy} onClick={() => void update("start")}><Play size={16} /> Start work</Button>}
          {workspace === "worker" && engagement.status === "IN_PROGRESS" && <Button className="button-block" busy={busy} onClick={() => void update("deliver")}><Upload size={16} /> Mark delivered</Button>}
          {workspace === "client" && engagement.status === "DELIVERED" && <><Button className="button-block" busy={busy} onClick={() => void update("approve")}><Check size={16} /> Approve work</Button><Button className="button-block" variant="secondary" onClick={() => setDialog("dispute")}><Flag size={16} /> Open dispute</Button></>}
          {workspace === "worker" && engagement.status === "DELIVERED" && <Alert tone="info">Delivery is awaiting client approval or dispute review.</Alert>}
          {engagement.status === "COMPLETED" && <Button className="button-block" variant="secondary" onClick={() => setDialog("review")}><Star size={16} /> Leave one review</Button>}
          {canCancel && <Button className="button-block" variant="danger" onClick={() => setDialog("cancel")}><X size={16} /> Cancel engagement</Button>}
          {terminalStatuses.includes(engagement.status) && engagement.status !== "COMPLETED" && <Alert tone="info">This engagement is read-only.</Alert>}
        </Card>
      </div>
    </div>
    <Modal title={dialog === "review" ? "Leave a review" : dialog === "deliverable" ? "Add delivery evidence" : dialog === "dispute" ? "Open a dispute" : "Cancel engagement"} open={Boolean(dialog)} onClose={() => setDialog(null)} footer={<><Button variant="quiet" onClick={() => setDialog(null)}>Back</Button><Button variant={dialog === "cancel" || dialog === "dispute" ? "danger" : "primary"} busy={busy} onClick={() => void submitDialog()}>{dialog === "review" ? "Submit review" : dialog === "deliverable" ? "Upload evidence" : "Confirm"}</Button></>}>
      {dialog === "review" && <div className="stack"><div className="field"><label>Rating</label><div className="star-picker">{[1, 2, 3, 4, 5].map((value) => <button type="button" key={value} className={value <= rating ? "active" : ""} onClick={() => setRating(value)} aria-label={`${value} stars`}><Star fill="currentColor" /></button>)}</div></div><div className="field"><label>Comment (optional)</label><textarea maxLength={2000} value={comment} onChange={(event) => setComment(event.target.value)} /><small>{comment.length} / 2000</small></div></div>}
      {(dialog === "cancel" || dialog === "dispute") && <div className="field"><label>{dialog === "dispute" ? "Reason *" : "Reason (optional)"}</label><textarea maxLength={2000} value={reason} onChange={(event) => setReason(event.target.value)} /></div>}
      {dialog === "deliverable" && <div className="stack"><Alert tone="info">JPEG, PNG, or WebP. Maximum 8 MB. Evidence is visible only to engagement participants and admins.</Alert><div className="field"><label>Image *</label><input type="file" accept="image/jpeg,image/png,image/webp" onChange={(event) => setEvidence(event.target.files?.[0] || null)} /></div><div className="field"><label>Note (optional)</label><input maxLength={300} value={evidenceNote} onChange={(event) => setEvidenceNote(event.target.value)} /></div></div>}
    </Modal>
  </div>;
}

function StripePaymentForm({ engagementId, amount, onConfirmed }: { engagementId: string; amount: string; onConfirmed: () => void }) {
  const stripe = useStripe();
  const elements = useElements();
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);

  const confirm = async (event: FormEvent) => {
    event.preventDefault();
    if (!stripe || !elements) return;
    setBusy(true); setProblem(null);
    const result = await stripe.confirmPayment({
      elements,
      confirmParams: { return_url: `${window.location.origin}/app/client/engagements/${engagementId}` },
      redirect: "if_required",
    });
    if (result.error) setProblem(result.error.message || "Stripe could not confirm this payment.");
    else onConfirmed();
    setBusy(false);
  };

  return <form className="stack stripe-payment-form" onSubmit={confirm}>
    {problem && <Alert tone="danger">{problem}</Alert>}
    <PaymentElement options={{ layout: "tabs" }} />
    <Button type="submit" className="button-block" busy={busy} disabled={!stripe || !elements}><CircleDollarSign size={16} /> Confirm {money(amount)} payment</Button>
    <small className="muted">Stripe securely collects payment details. The marketplace never receives or stores raw card data.</small>
  </form>;
}

export function PaymentPage() {
  const { engagementId = "" } = useParams();
  const navigate = useNavigate();
  const fallback = { engagement: engagementFallback(engagementId, "client"), config: { gateway: "mock", publishableKey: null } };
  const resource = useAsyncData(async () => ({ engagement: await api.engagements.get(engagementId), config: await api.payments.config() }), fallback, [engagementId]);
  const [busy, setBusy] = useState(false);
  const [payment, setPayment] = useState<Payment | null>(null);
  const [problem, setProblem] = useState<string | null>(null);
  const [requestId] = useState(() => `web-${engagementId}-${crypto.randomUUID()}`);
  const stripePromise = useMemo(() => resource.data.config.gateway === "stripe" && resource.data.config.publishableKey ? loadStripe(resource.data.config.publishableKey) : null, [resource.data.config.gateway, resource.data.config.publishableKey]);

  const pay = async () => {
    setBusy(true); setProblem(null);
    try {
      const result = await api.engagements.pay(engagementId, requestId);
      setPayment(result);
      if (result.status === "SUCCEEDED" || result.alreadyPaid) setTimeout(() => navigate(`/app/client/engagements/${engagementId}`), 900);
      else if (resource.data.config.gateway === "stripe" && !result.clientSecret) setProblem("Stripe did not return a confirmation secret. Retry with the same payment request or check the provider configuration.");
    } catch (caught) {
      if (resource.demo) {
        setPayment({ paymentId: 1, engagementId: Number(engagementId), requestId: "demo", amount: resource.data.engagement.amount, currency: "USD", status: "SUCCEEDED", paidAt: new Date().toISOString(), paymentIntentId: "mock_demo", providerStatus: "succeeded", failureMessage: null, alreadyPaid: false, requestIdMatched: false });
        setTimeout(() => navigate(`/app/client/engagements/${engagementId}`), 900);
      } else setProblem(caught instanceof Error ? caught.message : "Payment failed");
    } finally { setBusy(false); }
  };

  if (resource.loading) return <LoadingState label="Preparing payment" />;
  if (resource.error && !resource.demo) return <ErrorState message={resource.error.message} retry={() => void resource.reload()} />;
  return <div className="narrow-page">
    {resource.demo && <DemoNotice />}
    <Button variant="quiet" onClick={() => navigate(-1)}><ArrowLeft size={15} /> Back</Button>
    <PageTitle eyebrow="Secure funding" title="Fund the engagement" description="Funding must complete before the worker can start." />
    {problem && <Alert tone="warning">{problem}</Alert>}
    {payment?.status === "SUCCEEDED" && <Alert tone="success" title="Funding confirmed">The worker can now start the engagement.</Alert>}
    <Card className="panel payment-card">
      <div className="split"><div><span className="eyebrow">ENG-{resource.data.engagement.id}</span><h2>{resource.data.engagement.workerDisplayName}</h2></div><strong className="detail-price">{money(resource.data.engagement.amount)}</strong></div>
      <div className="summary-list"><div className="summary-item"><WalletCards size={17} /><div><span>Payment gateway</span><strong>{titleCase(resource.data.config.gateway)}{resource.data.config.gateway === "stripe" ? " · test/production key configured" : " · local demo processor"}</strong></div></div><div className="summary-item"><FileCheck2 size={17} /><div><span>What happens next</span><strong>The engagement moves to FUNDED after confirmation.</strong></div></div></div>
      {resource.data.config.gateway === "stripe" && !resource.data.config.publishableKey && <Alert tone="danger" title="Stripe is not configured">The backend selected Stripe but did not expose a publishable key.</Alert>}
      {resource.data.config.gateway === "stripe" && !payment?.clientSecret && <Button className="button-block" busy={busy} disabled={resource.data.engagement.status !== "ACCEPTED" || !resource.data.config.publishableKey} onClick={() => void pay()}><WalletCards size={16} /> Prepare secure payment</Button>}
      {resource.data.config.gateway === "stripe" && payment?.clientSecret && stripePromise && <Elements stripe={stripePromise} options={{ clientSecret: payment.clientSecret, appearance: { theme: "stripe", variables: { colorPrimary: "#127a4a", colorText: "#111827", borderRadius: "8px" } } }}><StripePaymentForm engagementId={engagementId} amount={resource.data.engagement.amount} onConfirmed={() => { setPayment((current) => current ? { ...current, status: "PENDING", providerStatus: "processing" } : current); setProblem("Payment was submitted. Stripe webhooks will confirm funding; the engagement may take a moment to update."); setTimeout(() => navigate(`/app/client/engagements/${engagementId}`), 1200); }} /></Elements>}
      {resource.data.config.gateway !== "stripe" && <Button className="button-block" busy={busy} disabled={resource.data.engagement.status !== "ACCEPTED"} onClick={() => void pay()}><CircleDollarSign size={16} /> Confirm {money(resource.data.engagement.amount)} funding</Button>}
    </Card>
  </div>;
}

export function RefundListPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const page = Math.max(0, Number(searchParams.get("page") || 0));
  const resource = useAsyncData(() => api.refunds.list(undefined, page, 20), asPage(demoRefunds, page, 20), [page]);
  return <div>{resource.demo && <DemoNotice />}<PageTitle eyebrow="Client workspace" title="Refunds" description="Track refunds created after cancellations or dispute resolutions." />{resource.loading ? <LoadingState /> : resource.error && !resource.demo ? <ErrorState message={resource.error.message} retry={() => void resource.reload()} /> : resource.data.items.length === 0 ? <Card><EmptyState title="No refunds" description="Any refund records will appear here." /></Card> : <Card className="data-card"><div className="table-wrap"><table className="data-table"><thead><tr><th>Refund</th><th>Engagement</th><th>Amount</th><th>Reason</th><th>Status</th><th /></tr></thead><tbody>{resource.data.items.map((item) => <tr key={item.id}><td>REF-{item.id}</td><td>ENG-{item.engagementId}</td><td>{money(item.amount)}</td><td>{item.reason}</td><td><Badge value={item.status} /></td><td><LinkButton variant="quiet" to={`/app/client/refunds/${item.id}`}>Details</LinkButton></td></tr>)}</tbody></table></div><Pagination page={resource.data.page} totalPages={resource.data.totalPages} onChange={(next) => setSearchParams({ page: String(next) })} /></Card>}</div>;
}

export function RefundDetailPage({ admin = false }: { admin?: boolean }) {
  const { refundId = "" } = useParams();
  const navigate = useNavigate();
  const fallback = demoRefunds.find((item) => String(item.id) === refundId) ?? demoRefunds[0];
  const resource = useAsyncData(() => api.refunds.get(refundId), fallback, [refundId]);
  if (resource.loading) return <LoadingState />;
  if (resource.error && !resource.demo) return <ErrorState message={resource.error.message} retry={() => void resource.reload()} />;
  const item = resource.data;
  return <div className="narrow-page">{resource.demo && <DemoNotice />}<Button variant="quiet" onClick={() => navigate(admin ? "/admin/finance/refunds" : "/app/client/refunds")}><ArrowLeft size={15} /> Back to refunds</Button><PageTitle eyebrow={`REF-${item.id}`} title="Refund details" description={`Engagement ENG-${item.engagementId}`} actions={<Badge value={item.status} />} />{item.status === "FAILED" && <Alert tone="danger" title="Provider refund failed">{item.failureMessage || "The provider did not complete this refund."}</Alert>}<Card className="panel"><div className="detail-price">{money(item.amount)}</div><div className="summary-list"><div className="summary-item"><FileCheck2 size={17} /><div><span>Reason</span><strong>{item.reason}</strong></div></div><div className="summary-item"><Clock3 size={17} /><div><span>Created</span><strong>{dateTime(item.createdAt)}</strong></div></div><div className="summary-item"><RefreshCw size={17} /><div><span>Provider state</span><strong>{item.providerStatus || "Not available"}</strong></div></div><div className="summary-item"><Check size={17} /><div><span>Completed</span><strong>{dateTime(item.refundedAt)}</strong></div></div></div><LinkButton className="button-block" variant="secondary" to={admin ? `/admin/disputes/${item.engagementId}` : `/app/client/engagements/${item.engagementId}`}>Open engagement</LinkButton></Card></div>;
}

function settlementSummary(items: Settlement[]): SettlementSummary {
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

export function EarningsPage() {
  const resource = useAsyncData(async () => { const [summary, settlements] = await Promise.all([api.settlements.summary(), api.settlements.list(undefined, 0, 100)]); return { summary, settlements }; }, { summary: settlementSummary(demoSettlements), settlements: asPage(demoSettlements, 0, 100) }, []);
  return <div>{resource.demo && <DemoNotice />}<PageTitle eyebrow="Worker workspace" title="Earnings" description="See platform fees, payout amounts, and settlement processing status." />{resource.loading ? <LoadingState /> : resource.error && !resource.demo ? <ErrorState message={resource.error.message} retry={() => void resource.reload()} /> : <><div className="stats-grid"><StatCard label="Total earnings" value={money(resource.data.summary.totalEarnings)} tone="green" /><StatCard label="Completed" value={money(resource.data.summary.completedAmount)} hint={`${resource.data.summary.completedCount} settlements`} tone="blue" /><StatCard label="Pending" value={money(resource.data.summary.pendingAmount)} hint={`${resource.data.summary.pendingCount} settlements`} tone="violet" /><StatCard label="Failed" value={resource.data.summary.failedCount} hint="Requires platform review" /></div><SettlementTable items={resource.data.settlements.items} base="/app/worker/earnings" /></>}</div>;
}

function SettlementTable({ items, base }: { items: Settlement[]; base: string }) {
  return items.length === 0 ? <Card><EmptyState title="No settlements" description="Completed engagements will create payout records." /></Card> : <Card className="data-card"><div className="table-wrap"><table className="data-table"><thead><tr><th>Settlement</th><th>Engagement</th><th>Gross</th><th>Platform fee</th><th>Payout</th><th>Status</th><th /></tr></thead><tbody>{items.map((item) => <tr key={item.id}><td>SET-{item.id}</td><td>ENG-{item.engagementId}</td><td>{money(item.totalAmount)}</td><td>{money(item.platformFee)}</td><td><strong>{money(item.workerPayout)}</strong></td><td><Badge value={item.status} /></td><td><LinkButton variant="quiet" to={`${base}/${item.id}`}>Details</LinkButton></td></tr>)}</tbody></table></div></Card>;
}

export function SettlementDetailPage({ admin = false }: { admin?: boolean }) {
  const { settlementId = "" } = useParams();
  const navigate = useNavigate();
  const fallback = demoSettlements.find((item) => String(item.id) === settlementId) ?? demoSettlements[0];
  const resource = useAsyncData(() => api.settlements.get(settlementId), fallback, [settlementId]);
  if (resource.loading) return <LoadingState />;
  if (resource.error && !resource.demo) return <ErrorState message={resource.error.message} retry={() => void resource.reload()} />;
  const item = resource.data;
  return <div className="narrow-page">{resource.demo && <DemoNotice />}<Button variant="quiet" onClick={() => navigate(admin ? "/admin/finance" : "/app/worker/earnings")}><ArrowLeft size={15} /> Back</Button><PageTitle eyebrow={`SET-${item.id}`} title="Settlement details" description={`Engagement ENG-${item.engagementId}`} actions={<Badge value={item.status} />} /><Card className="panel"><div className="settlement-math"><div><span>Gross amount</span><strong>{money(item.totalAmount)}</strong></div><span>−</span><div><span>Platform fee</span><strong>{money(item.platformFee)}</strong></div><span>=</span><div><span>Worker payout</span><strong>{money(item.workerPayout)}</strong></div></div><div className="summary-list"><div className="summary-item"><Clock3 size={17} /><div><span>Created</span><strong>{dateTime(item.createdAt)}</strong></div></div><div className="summary-item"><Check size={17} /><div><span>Settled</span><strong>{dateTime(item.settledAt)}</strong></div></div></div><LinkButton className="button-block" variant="secondary" to={admin ? `/admin/disputes/${item.engagementId}` : `/app/worker/engagements/${item.engagementId}`}>Open engagement</LinkButton></Card></div>;
}

export function ReputationPage({ workspace }: { workspace: Workspace }) {
  const { user } = useAuth();
  const expectedDirection = workspace === "client" ? "WORKER_TO_CLIENT" : "CLIENT_TO_WORKER";
  const fallbackItems = demoReviews.filter((item) => item.direction === expectedDirection);
  const resource = useAsyncData(() => user ? api.reviews.forUser(user.id, 0, 100) : Promise.resolve(asPage<Review>([])), asPage(fallbackItems), [user?.id, workspace]);
  const items = resource.data.items.filter((item) => item.direction === expectedDirection);
  const average = items.length ? items.reduce((sum, item) => sum + item.rating, 0) / items.length : 0;
  return <div>{resource.demo && <DemoNotice />}<PageTitle eyebrow={`${workspace} workspace`} title="Reputation" description={workspace === "client" ? "Reviews workers have left after completed engagements." : "Verified feedback from clients after completed engagements."} />{resource.loading ? <LoadingState /> : resource.error && !resource.demo ? <ErrorState message={resource.error.message} retry={() => void resource.reload()} /> : <div className="content-grid"><Card className="panel reputation-summary"><span className="eyebrow">Average rating</span><strong>{average ? average.toFixed(1) : "—"}</strong><Rating value={average} count={items.length} /><p>Reviews are tied to completed engagements and each participant can submit once.</p></Card><Card className="panel"><h2>Review history</h2>{items.length === 0 ? <EmptyState title="No reviews yet" description="Completed work can receive one review from the other participant." /> : <div className="stack">{items.map((item) => <article className="review-row" key={item.id}><Avatar name={item.reviewer.name} /><div><div className="split"><strong>{item.reviewer.name}</strong><Rating value={item.rating} compact /></div><p>{item.comment || "No written comment."}</p><small>{date(item.createdAt)} · ENG-{item.engagementId}</small></div></article>)}</div>}</Card></div>}</div>;
}
