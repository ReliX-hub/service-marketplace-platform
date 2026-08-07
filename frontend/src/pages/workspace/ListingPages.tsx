import { ArrowLeft, Check, Eye, FileText, ImagePlus, LockKeyhole, Plus, Send, X } from "lucide-react";
import { useEffect, useMemo, useState, type FormEvent } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { api, ApiError } from "../../lib/api";
import { asPage, demoApplications, demoCategories, demoTicketDetails, demoTickets } from "../../data/demo";
import { date, dateTime, locationLabels, money, ticketPrice, titleCase } from "../../lib/format";
import { useAsyncData } from "../../hooks/useAsyncData";
import type { Application, ApplicationStatus, Category, PricingMode, TicketDetail, TicketKind, TicketStatus, TicketSummary } from "../../types";
import { Alert, Badge, Button, Card, DemoNotice, EmptyState, LinkButton, LoadingState, Modal, PageTitle } from "../../components/ui";

export function MyListingsPage({ kind, workspace }: { kind: TicketKind; workspace: "client" | "worker" }) {
  const [searchParams, setSearchParams] = useSearchParams();
  const status = (searchParams.get("status") || "") as TicketStatus | "";
  const fallback = asPage(demoTickets.filter((ticket) => ticket.kind === kind));
  const resource = useAsyncData(() => api.tickets.mine({ kind, status: status || undefined, size: 100 }), fallback, [kind, status]);
  const label = kind === "REQUEST" ? "requests" : "services";
  const routeSegment = kind === "REQUEST" ? "requests" : "offers";

  return (
    <div>
      {resource.demo && <DemoNotice />}
      <PageTitle
        eyebrow={`${workspace} workspace`}
        title={`My ${label}`}
        description={kind === "REQUEST" ? "Manage the tasks you have posted and compare proposals." : "Manage the services clients can discover and request."}
        actions={<LinkButton to={`/app/${workspace}/${routeSegment}/new`}><Plus size={16} /> Create {kind === "REQUEST" ? "request" : "service"}</LinkButton>}
      />
      <div className="tabs">
        {["", "DRAFT", "OPEN", "MATCHED", "CLOSED", "EXPIRED"].map((value) => (
          <button key={value || "ALL"} className={status === value ? "active" : ""} onClick={() => setSearchParams(value ? { status: value } : {})}>{value ? titleCase(value) : "All"}</button>
        ))}
      </div>
      {resource.loading ? <LoadingState label={`Loading ${label}`} /> : resource.data.items.length === 0 ? (
        <Card><EmptyState title={`No ${label} here`} description={`Create your first ${kind.toLowerCase()} or choose a different status.`} action={<LinkButton to={`/app/${workspace}/${routeSegment}/new`}>Create listing</LinkButton>} /></Card>
      ) : (
        <Card className="data-card">
          <div className="table-wrap">
            <table className="data-table">
              <thead><tr><th>Listing</th><th>Pricing</th><th>Location</th><th>Responses</th><th>Status</th><th>Updated</th><th /></tr></thead>
              <tbody>
                {resource.data.items.map((ticket) => (
                  <tr key={ticket.id}>
                    <td><span className="table-primary">{ticket.title}</span><span className="table-secondary">#{ticket.id} · {ticket.category.name}</span></td>
                    <td>{ticketPrice(ticket)}</td>
                    <td>{locationLabels[ticket.locationMode]}{ticket.city ? ` · ${ticket.city}` : ""}</td>
                    <td>{ticket.applicationCount}</td>
                    <td><Badge value={ticket.status} /></td>
                    <td>{date(ticket.updatedAt)}</td>
                    <td><div className="table-actions"><LinkButton to={`/app/${workspace}/${routeSegment}/${ticket.id}`} variant="quiet"><Eye size={14} /> Open</LinkButton></div></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}
    </div>
  );
}

export function ListingManagementPage({ kind, workspace }: { kind: TicketKind; workspace: "client" | "worker" }) {
  const { ticketId = "" } = useParams();
  const navigate = useNavigate();
  const label = kind === "REQUEST" ? "requests" : "offers";
  const fallback = demoTicketDetails.find((ticket) => String(ticket.id) === ticketId && ticket.kind === kind) || demoTicketDetails.find((ticket) => ticket.kind === kind)!;
  const resource = useAsyncData(() => api.tickets.mineOne(ticketId), fallback, [ticketId]);
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  const ticket = resource.data;

  const perform = async (action: "publish" | "close") => {
    setBusy(true);
    setProblem(null);
    try {
      const updated = action === "publish" ? await api.tickets.publish(ticket.id) : await api.tickets.close(ticket.id);
      resource.setData(updated);
    } catch (caught) {
      if (resource.demo) resource.setData((current) => ({ ...current, status: action === "publish" ? "OPEN" : "CLOSED" }));
      else setProblem(caught instanceof Error ? caught.message : "Action failed");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div>
      {resource.demo && <DemoNotice />}
      <button className="button button-quiet" onClick={() => navigate(`/app/${workspace}/${label}`)}><ArrowLeft size={15} /> Back to listings</button>
      <PageTitle
        eyebrow={`${kind} · #${ticket.id}`}
        title={ticket.title}
        description={`${ticket.category.name} · ${locationLabels[ticket.locationMode]}${ticket.city ? ` · ${ticket.city}` : ""}`}
        actions={
          <>
            {(ticket.status === "DRAFT" || ticket.status === "OPEN" || ticket.status === "MATCHED") && <LinkButton variant="secondary" to={`/app/${workspace}/${label}/${ticket.id}/edit`}>Edit</LinkButton>}
            {ticket.status === "DRAFT" && <Button busy={busy} onClick={() => void perform("publish")}>Publish listing</Button>}
            {["OPEN", "MATCHED"].includes(ticket.status) && <Button variant="danger" busy={busy} onClick={() => void perform("close")}>Close listing</Button>}
          </>
        }
      />
      {problem && <Alert tone="danger">{problem}</Alert>}
      <div className="content-grid">
        <Card className="panel">
          <div className="cluster"><Badge value={kind} /><Badge value={ticket.status} /></div>
          <h2 style={{ marginTop: 18 }}>Listing details</h2>
          <p>{ticket.description || "No description provided."}</p>
          <div className="form-grid">
            <div className="field"><span className="field-label">Pricing</span><strong>{ticketPrice(ticket)}</strong></div>
            <div className="field"><span className="field-label">Service window</span><strong>{ticket.serviceWindowStart ? `${dateTime(ticket.serviceWindowStart)} – ${dateTime(ticket.serviceWindowEnd)}` : "Anytime"}</strong></div>
            <div className="field"><span className="field-label">Application deadline</span><strong>{dateTime(ticket.expiresAt)}</strong></div>
            <div className="field"><span className="field-label">Responses</span><strong>{ticket.applicationCount}</strong></div>
          </div>
          {ticket.images.length > 0 && <div className="photo-grid">{ticket.images.map((item) => <img key={item.id} src={item.image.thumb} alt={item.caption || "Listing"} />)}</div>}
        </Card>
        <div className="stack">
          <Card className="panel">
            <h2>Response management</h2>
            <p>{kind === "REQUEST" ? "Workers submit proposals. You decide which proposal to accept." : "Clients request your service. You decide which request to accept."}</p>
            <LinkButton className="button-block" to={`/app/${workspace}/${label}/${ticket.id}/applications`}>View {ticket.applicationCount} responses</LinkButton>
          </Card>
          {ticket.status === "MATCHED" && <Alert tone="info" title="Core terms are locked">Category, pricing, currency, service window, and deadline cannot change after matching.</Alert>}
          <Alert tone="info" title="Current Ticket API">Create, update, publish, and close are supported. Delete, duplicate, reopen, and republish are not available.</Alert>
        </div>
      </div>
    </div>
  );
}

interface ComposerForm {
  categoryId: string;
  title: string;
  description: string;
  pricingMode: PricingMode;
  price: string;
  budgetMin: string;
  budgetMax: string;
  locationMode: "ON_SITE" | "REMOTE" | "HYBRID";
  address: string;
  city: string;
  estimatedDurationMinutes: string;
  serviceWindowStart: string;
  serviceWindowEnd: string;
  expiresAt: string;
}

const emptyComposer: ComposerForm = {
  categoryId: "",
  title: "",
  description: "",
  pricingMode: "FIXED",
  price: "",
  budgetMin: "",
  budgetMax: "",
  locationMode: "ON_SITE",
  address: "",
  city: "Chicago",
  estimatedDurationMinutes: "",
  serviceWindowStart: "",
  serviceWindowEnd: "",
  expiresAt: "",
};

function toIso(value: string) {
  return value ? new Date(value).toISOString() : undefined;
}

export function ListingComposerPage({ kind, workspace }: { kind: TicketKind; workspace: "client" | "worker" }) {
  const { ticketId } = useParams();
  const navigate = useNavigate();
  const [step, setStep] = useState(1);
  const [form, setForm] = useState<ComposerForm>(emptyComposer);
  const [files, setFiles] = useState<File[]>([]);
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  const categories = useAsyncData<Category[]>(() => api.categories.list(), demoCategories, []);
  const label = kind === "REQUEST" ? "requests" : "offers";

  useEffect(() => {
    if (!ticketId) return;
    void api.tickets.mineOne(ticketId).then((ticket) => {
      setForm({
        categoryId: String(ticket.category.id), title: ticket.title, description: ticket.description || "",
        pricingMode: ticket.pricingMode, price: ticket.price || "", budgetMin: ticket.budgetMin || "", budgetMax: ticket.budgetMax || "",
        locationMode: ticket.locationMode, address: ticket.address || "", city: ticket.city || "", estimatedDurationMinutes: ticket.estimatedDurationMinutes ? String(ticket.estimatedDurationMinutes) : "",
        serviceWindowStart: ticket.serviceWindowStart ? ticket.serviceWindowStart.slice(0, 16) : "", serviceWindowEnd: ticket.serviceWindowEnd ? ticket.serviceWindowEnd.slice(0, 16) : "", expiresAt: ticket.expiresAt ? ticket.expiresAt.slice(0, 16) : "",
      });
    }).catch(() => undefined);
  }, [ticketId]);

  const set = <K extends keyof ComposerForm>(key: K, value: ComposerForm[K]) => setForm((current) => ({ ...current, [key]: value }));
  const selectedCategory = categories.data.find((category) => String(category.id) === form.categoryId);
  const validateStep = () => {
    if (step === 1 && (!form.categoryId || !form.title.trim())) return "Choose a category and enter a title.";
    if (step === 2 && form.pricingMode === "FIXED" && !form.price) return "Enter a fixed price.";
    if (step === 2 && form.pricingMode === "BUDGET_RANGE" && (!form.budgetMin || !form.budgetMax)) return "Enter the full budget range.";
    return null;
  };

  const next = () => {
    const issue = validateStep();
    setProblem(issue);
    if (!issue) setStep((value) => Math.min(4, value + 1));
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setProblem(null);
    const body: Record<string, unknown> = {
      kind,
      categoryId: Number(form.categoryId),
      title: form.title.trim(),
      description: form.description.trim() || undefined,
      pricingMode: form.pricingMode,
      price: form.pricingMode === "FIXED" ? form.price : undefined,
      budgetMin: form.pricingMode === "BUDGET_RANGE" ? form.budgetMin : undefined,
      budgetMax: form.pricingMode === "BUDGET_RANGE" ? form.budgetMax : undefined,
      currency: "USD",
      locationMode: form.locationMode,
      address: form.address.trim() || undefined,
      city: form.city.trim() || undefined,
      estimatedDurationMinutes: form.estimatedDurationMinutes ? Number(form.estimatedDurationMinutes) : undefined,
      serviceWindowStart: toIso(form.serviceWindowStart),
      serviceWindowEnd: toIso(form.serviceWindowEnd),
      expiresAt: toIso(form.expiresAt),
    };
    try {
      const ticket = ticketId ? await api.tickets.update(ticketId, body) : await api.tickets.create(body);
      for (const file of files) await api.tickets.uploadImage(ticket.id, file);
      navigate(`/app/${workspace}/${label}/${ticket.id}`);
    } catch (caught) {
      if (caught instanceof ApiError && caught.code === "CREDENTIAL_REQUIRED") {
        setProblem("A verified credential is required for this category before the OFFER draft can be created or published.");
      } else if (caught instanceof ApiError && caught.status === 0) {
        navigate(`/app/${workspace}/${label}/${kind === "REQUEST" ? 201 : 101}`);
      } else setProblem(caught instanceof Error ? caught.message : "Unable to save listing");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div>
      <PageTitle eyebrow={`${kind} composer`} title={ticketId ? "Edit listing" : kind === "REQUEST" ? "Post a task" : "Create a service"} description="Start with the essentials, then review the complete listing before publishing." />
      <div className="stepper">{["Basics", "Price & place", "Photos", "Review"].map((labelText, index) => <button type="button" key={labelText} className={`step ${step >= index + 1 ? "active" : ""}`} onClick={() => setStep(index + 1)}><span>{step > index + 1 ? <Check size={14} /> : index + 1}</span>{labelText}</button>)}</div>
      {problem && <Alert tone="danger">{problem}</Alert>}
      <form className="composer-grid" onSubmit={submit} style={{ marginTop: 16 }}>
        <Card className="composer-main">
          {step === 1 && <div className="stack">
            <Alert tone="info" title={`${kind} is fixed after creation`}>{kind === "REQUEST" ? "You are posting a task that workers can propose to." : "You are listing a service that clients can request."}</Alert>
            <div className="field"><label>Category *</label><select value={form.categoryId} onChange={(e) => set("categoryId", e.target.value)}><option value="">Select a category</option>{categories.data.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}</select></div>
            <div className="field"><label>Title *</label><input maxLength={200} value={form.title} onChange={(e) => set("title", e.target.value)} placeholder="Write a clear, specific title" /><small>{form.title.length} / 200</small></div>
            <div className="field"><label>Description (optional)</label><textarea value={form.description} onChange={(e) => set("description", e.target.value)} placeholder="Describe scope and expectations" /></div>
          </div>}
          {step === 2 && <div className="stack">
            <div className="field"><label>Pricing mode *</label><div className="segmented">{(["FIXED", "BUDGET_RANGE", "OPEN_BID"] as PricingMode[]).map((value) => <button type="button" key={value} className={form.pricingMode === value ? "active" : ""} onClick={() => set("pricingMode", value)}>{titleCase(value)}</button>)}</div></div>
            {form.pricingMode === "FIXED" && <div className="field"><label>Fixed price (USD) *</label><input type="number" min="0" step="0.01" value={form.price} onChange={(e) => set("price", e.target.value)} /></div>}
            {form.pricingMode === "BUDGET_RANGE" && <div className="form-grid"><div className="field"><label>Minimum (USD) *</label><input type="number" min="0" step="0.01" value={form.budgetMin} onChange={(e) => set("budgetMin", e.target.value)} /></div><div className="field"><label>Maximum (USD) *</label><input type="number" min="0" step="0.01" value={form.budgetMax} onChange={(e) => set("budgetMax", e.target.value)} /></div></div>}
            <div className="field"><label>Location mode *</label><div className="segmented">{(["ON_SITE", "REMOTE", "HYBRID"] as const).map((value) => <button type="button" key={value} className={form.locationMode === value ? "active" : ""} onClick={() => set("locationMode", value)}>{locationLabels[value]}</button>)}</div></div>
            <div className="form-grid"><div className="field"><label>Address</label><input value={form.address} onChange={(e) => set("address", e.target.value)} /></div><div className="field"><label>City</label><input value={form.city} onChange={(e) => set("city", e.target.value)} /></div></div>
            <div className="field"><label>Estimated duration (minutes)</label><input type="number" min="1" value={form.estimatedDurationMinutes} onChange={(e) => set("estimatedDurationMinutes", e.target.value)} /></div>
            <div className="form-grid"><div className="field"><label>Service window start</label><input type="datetime-local" value={form.serviceWindowStart} onChange={(e) => set("serviceWindowStart", e.target.value)} /></div><div className="field"><label>Service window end</label><input type="datetime-local" value={form.serviceWindowEnd} onChange={(e) => set("serviceWindowEnd", e.target.value)} /></div></div>
            <div className="field"><label>Application deadline</label><input type="datetime-local" value={form.expiresAt} onChange={(e) => set("expiresAt", e.target.value)} /></div>
          </div>}
          {step === 3 && <div className="stack">
            <label className="dropzone"><ImagePlus size={30} /><strong>Drag and drop photos here or choose files</strong><span>JPEG, PNG, WebP · Up to 8 MB each · Max 8</span><input type="file" multiple accept="image/jpeg,image/png,image/webp" hidden onChange={(event) => setFiles(Array.from(event.target.files || []).slice(0, 8))} /><Button type="button" variant="secondary" onClick={(event) => ((event.currentTarget.parentElement?.querySelector("input") as HTMLInputElement)?.click())}>Choose files</Button></label>
            {files.length > 0 && <div className="photo-grid">{files.map((file) => <img key={file.name} src={URL.createObjectURL(file)} alt="" />)}</div>}
            <Alert tone="info">Photos are optional. Draft photos stay private until the listing is published.</Alert>
          </div>}
          {step === 4 && <div className="stack">
            <div className="cluster"><Badge value={kind} /><Badge value="DRAFT" /></div>
            <h2>{form.title || "Untitled listing"}</h2>
            <p>{form.description || "No description provided."}</p>
            <div className="form-grid"><div className="field"><span className="field-label">Category</span><strong>{selectedCategory?.name || "—"}</strong></div><div className="field"><span className="field-label">Pricing</span><strong>{form.pricingMode === "FIXED" ? money(form.price) : form.pricingMode === "BUDGET_RANGE" ? `${money(form.budgetMin)}–${money(form.budgetMax)}` : "Open bid"}</strong></div><div className="field"><span className="field-label">Location</span><strong>{locationLabels[form.locationMode]} · {form.city || "No city"}</strong></div><div className="field"><span className="field-label">Photos</span><strong>{files.length}</strong></div></div>
            {selectedCategory?.requiredCredential && kind === "OFFER" && <Alert tone="warning" title="Credential gate">A verified {titleCase(selectedCategory.requiredCredential)} is required to create this OFFER draft, change to this category, or publish.</Alert>}
          </div>}
          <div className="split" style={{ marginTop: 24 }}><Button type="button" variant="quiet" disabled={step === 1} onClick={() => setStep((value) => Math.max(1, value - 1))}>Back</Button>{step < 4 ? <Button type="button" onClick={next}>Continue</Button> : <Button type="submit" busy={busy}><Send size={15} /> {ticketId ? "Save changes" : "Create draft"}</Button>}</div>
        </Card>
        <Card className="composer-preview">
          <span className="eyebrow">Live preview</span>
          <div className="cluster"><Badge value={kind} /> <Badge value="DRAFT" /></div>
          <h2 style={{ marginTop: 16 }}>{form.title || "Your listing title"}</h2>
          <p>{form.description || "A concise preview of your service or task will appear here."}</p>
          <strong className="detail-price">{form.pricingMode === "FIXED" ? money(form.price || 0) : form.pricingMode === "BUDGET_RANGE" ? `${money(form.budgetMin || 0)}–${money(form.budgetMax || 0)}` : "Open bid"}</strong>
          <div className="summary-list"><div className="summary-item"><LockKeyhole size={16} /><div><span>Kind</span><strong>Locked as {kind}</strong></div></div><div className="summary-item"><FileText size={16} /><div><span>Category</span><strong>{selectedCategory?.name || "Choose a category"}</strong></div></div></div>
        </Card>
      </form>
    </div>
  );
}

export function ApplicationsPage({ workspace, direction }: { workspace: "client" | "worker"; direction: "incoming" | "outgoing" }) {
  const { ticketId } = useParams();
  const wantedKind: TicketKind = workspace === "client" ? (direction === "incoming" ? "REQUEST" : "OFFER") : direction === "incoming" ? "OFFER" : "REQUEST";
  const [status, setStatus] = useState<ApplicationStatus | "">("");
  const fallbackItems = demoApplications.filter((item) => item.ticketKind === wantedKind);
  const fallback = asPage(fallbackItems);
  const resource = useAsyncData(
    async () => direction === "incoming" && ticketId
      ? api.applications.forTicket(ticketId, status || undefined, 0, 100)
      : api.applications.mine(status || undefined, 0, 100).then((page) => ({ ...page, items: page.items.filter((item) => item.ticketKind === wantedKind), totalElements: page.items.filter((item) => item.ticketKind === wantedKind).length })),
    fallback,
    [ticketId, status, direction, wantedKind],
  );
  const [confirm, setConfirm] = useState<{ item: Application; action: "accept" | "reject" | "withdraw" } | null>(null);
  const [busy, setBusy] = useState(false);

  const act = async () => {
    if (!confirm) return;
    setBusy(true);
    try {
      const updated = confirm.action === "accept" ? await api.applications.accept(confirm.item.id) : confirm.action === "reject" ? await api.applications.reject(confirm.item.id) : await api.applications.withdraw(confirm.item.id);
      resource.setData((page) => ({ ...page, items: page.items.map((item) => item.id === updated.id ? updated : item) }));
    } catch {
      resource.setData((page) => ({ ...page, items: page.items.map((item) => item.id === confirm.item.id ? { ...item, status: confirm.action === "accept" ? "ACCEPTED" : confirm.action === "reject" ? "REJECTED" : "WITHDRAWN" } : item) }));
    } finally { setBusy(false); setConfirm(null); }
  };

  return (
    <div>
      {resource.demo && <DemoNotice />}
      <PageTitle eyebrow={`${workspace} workspace`} title={direction === "incoming" ? "Incoming responses" : workspace === "client" ? "Service requests sent" : "Proposals submitted"} description={direction === "incoming" ? "Compare scope, proposed price, and schedule before deciding." : "Track every response you have sent to an open listing."} />
      {!ticketId && <Alert tone="warning" title="API filtering limitation">The current `/me/applications` endpoint cannot filter by Ticket kind. This page filters the loaded page locally, so counts may be partial across larger datasets.</Alert>}
      <div className="tabs" style={{ marginTop: 18 }}>{(["", "PENDING", "ACCEPTED", "REJECTED", "WITHDRAWN"] as const).map((value) => <button key={value || "ALL"} className={status === value ? "active" : ""} onClick={() => setStatus(value)}>{value ? titleCase(value) : "All"}</button>)}</div>
      {resource.loading ? <LoadingState /> : resource.data.items.length === 0 ? <Card><EmptyState title="No responses found" description="Nothing matches this status yet." /></Card> : <Card className="data-card"><div className="table-wrap"><table className="data-table"><thead><tr><th>Listing</th><th>{direction === "incoming" ? "Applicant" : "Direction"}</th><th>Proposal</th><th>Schedule</th><th>Status</th><th /></tr></thead><tbody>{resource.data.items.map((item) => <tr key={item.id}><td><span className="table-primary">{item.ticketTitle}</span><span className="table-secondary">#{item.ticketId} · {item.ticketKind}</span></td><td>{direction === "incoming" ? item.applicantName : item.ticketKind === "OFFER" ? "Service request" : "Proposal"}</td><td>{money(item.proposedAmount)}</td><td>{dateTime(item.proposedStart)}</td><td><Badge value={item.status} /></td><td><div className="table-actions">{direction === "incoming" && item.status === "PENDING" && <><Button onClick={() => setConfirm({ item, action: "accept" })}>Accept</Button><Button variant="danger" onClick={() => setConfirm({ item, action: "reject" })}>Reject</Button></>}{direction === "outgoing" && item.status === "PENDING" && <Button variant="danger" onClick={() => setConfirm({ item, action: "withdraw" })}>Withdraw</Button>}{item.status === "ACCEPTED" && item.engagementId && <LinkButton to={`/app/${workspace}/engagements/${item.engagementId}`}>Open engagement</LinkButton>}</div></td></tr>)}</tbody></table></div></Card>}
      <Modal title={`${confirm ? titleCase(confirm.action) : "Update"} response?`} open={Boolean(confirm)} onClose={() => setConfirm(null)} footer={<><Button variant="quiet" onClick={() => setConfirm(null)}>Cancel</Button><Button variant={confirm?.action === "accept" ? "primary" : "danger"} busy={busy} onClick={() => void act()}>{confirm?.action === "accept" ? <Check size={15} /> : <X size={15} />}{confirm ? titleCase(confirm.action) : "Confirm"}</Button></>}><p>{confirm?.action === "accept" ? "Accepting creates one engagement and rejects remaining pending responses." : "This updates the response immediately."}</p></Modal>
    </div>
  );
}
