import {
  ArrowLeft,
  BadgeCheck,
  CalendarDays,
  Check,
  CheckCircle2,
  Eye,
  FileImage,
  ImageUp,
  LockKeyhole,
  MapPin,
  Plus,
  RefreshCw,
  ShieldCheck,
  UploadCloud,
} from "lucide-react";
import { useEffect, useMemo, useRef, useState, type ChangeEvent, type DragEvent, type FormEvent } from "react";
import { Link, useLocation, useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../../auth/AuthContext";
import {
  Alert,
  Avatar,
  Badge,
  Button,
  Card,
  DemoNotice,
  EmptyState,
  ErrorState,
  LinkButton,
  LoadingState,
  PageTitle,
  Pagination,
  Rating,
} from "../../components/ui";
import { asPage, demoCredentials, demoWorkers } from "../../data/demo";
import { useAsyncData } from "../../hooks/useAsyncData";
import { api, ApiError, assetUrl, authenticatedBlobUrl } from "../../lib/api";
import { date, dateTime, titleCase } from "../../lib/format";
import type { Credential, CredentialStatus, CredentialType, UserResponse, WorkerProfile } from "../../types";

const credentialTypes: Array<{ value: CredentialType; label: string; hint: string }> = [
  { value: "ELECTRICAL_LICENSE", label: "Electrical license", hint: "Required for regulated electrical categories." },
  { value: "DRIVER_LICENSE", label: "Driver license", hint: "May be required for moving and driving-related work." },
  { value: "BACKGROUND_CHECK", label: "Background check", hint: "May be required for trust-sensitive categories." },
];

const allowedImageTypes = new Set(["image/jpeg", "image/png", "image/webp"]);
const maxImageBytes = 8 * 1024 * 1024;
const today = () => new Date().toISOString().slice(0, 10);

function safeProfile(user: UserResponse | null): WorkerProfile {
  return {
    id: 0,
    userId: user?.id || 0,
    displayName: user?.name || "",
    headline: null,
    description: null,
    address: null,
    rating: "0.00",
    reviewCount: 0,
    verified: false,
    completedJobs: 0,
    serviceRadiusKm: null,
    recentWork: [],
    createdAt: user?.createdAt || new Date().toISOString(),
  };
}

async function findCurrentPublicProfile(user: UserResponse): Promise<{ profile: WorkerProfile; exists: boolean }> {
  const credentials = await api.credentials.mine(0, 1);
  const identity = credentials.items.find((credential) => credential.workerUserId === user.id);
  if (identity) {
    return { profile: await api.workers.get(identity.workerId), exists: true };
  }
  return { profile: safeProfile(user), exists: false };
}

interface ProfileFields {
  displayName: string;
  headline: string;
  description: string;
  address: string;
  serviceRadiusKm: string;
}

type ProfileErrors = Partial<Record<keyof ProfileFields | "form", string>>;

function fieldsFromProfile(profile: WorkerProfile): ProfileFields {
  return {
    displayName: profile.displayName || "",
    headline: profile.headline || "",
    description: profile.description || "",
    address: profile.address || "",
    serviceRadiusKm: profile.serviceRadiusKm || "",
  };
}

function validateProfile(fields: ProfileFields): ProfileErrors {
  const errors: ProfileErrors = {};
  if (!fields.displayName.trim()) errors.displayName = "Display name is required.";
  else if (fields.displayName.trim().length > 200) errors.displayName = "Display name must be 200 characters or fewer.";
  if (fields.headline.length > 255) errors.headline = "Headline must be 255 characters or fewer.";
  if (fields.address.length > 500) errors.address = "Address must be 500 characters or fewer.";
  if (fields.serviceRadiusKm && (Number.isNaN(Number(fields.serviceRadiusKm)) || Number(fields.serviceRadiusKm) < 0 || Number(fields.serviceRadiusKm) > 999_999.99 || !/^\d+(\.\d{1,2})?$/.test(fields.serviceRadiusKm))) errors.serviceRadiusKm = "Enter 0–999,999.99 with at most two decimal places.";
  return errors;
}

function profileApiErrors(problem: ApiError): ProfileErrors {
  const keyByField: Record<string, keyof ProfileFields> = {
    displayName: "displayName",
    headline: "headline",
    description: "description",
    address: "address",
    serviceRadiusKm: "serviceRadiusKm",
  };
  const field = problem.field ? keyByField[problem.field] : undefined;
  return field ? { [field]: problem.message } : { form: problem.message };
}

export function WorkerProfileSettingsPage() {
  const { user, loading: authLoading, demo: authDemo } = useAuth();
  const matchedDemoProfile = demoWorkers.find((item) => item.userId === user?.id);
  const fallback = useMemo(
    () => ({ profile: matchedDemoProfile || safeProfile(user), exists: Boolean(matchedDemoProfile) }),
    [matchedDemoProfile, user],
  );
  const resource = useAsyncData(
    async () => {
      if (!user) throw new ApiError("Sign in to manage a worker profile", 401, "UNAUTHENTICATED");
      return findCurrentPublicProfile(user);
    },
    fallback,
    [user?.id],
  );
  const [fields, setFields] = useState<ProfileFields>(() => fieldsFromProfile(fallback.profile));
  const [errors, setErrors] = useState<ProfileErrors>({});
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const hydratedUser = useRef<number | null>(null);

  useEffect(() => {
    if (!resource.loading && user && hydratedUser.current !== user.id) {
      setFields(fieldsFromProfile(resource.data.profile));
      hydratedUser.current = user.id;
    }
  }, [resource.loading, resource.data.profile, user]);

  if (authLoading || resource.loading) return <LoadingState label="Loading worker profile" />;
  if (!user) return <EmptyState title="Sign in required" description="Sign in to manage your worker profile." action={<LinkButton to="/login">Sign in</LinkButton>} />;
  if (resource.error && !resource.demo) return <ErrorState message={resource.error.message} retry={() => void resource.reload()} />;

  const profile = resource.data.profile;
  const reset = () => {
    setFields(fieldsFromProfile(profile));
    setErrors({});
    setSaved(false);
  };

  const save = async (event: FormEvent) => {
    event.preventDefault();
    setSaved(false);
    const nextErrors = validateProfile(fields);
    if (Object.keys(nextErrors).length) {
      setErrors(nextErrors);
      return;
    }
    if (resource.demo || authDemo) {
      setErrors({ form: "Connect the backend before saving profile changes. Demo profile values are read-only." });
      return;
    }
    setSaving(true);
    setErrors({});
    try {
      const updated = await api.workers.saveProfile({
        displayName: fields.displayName.trim(),
        headline: fields.headline.trim() || null,
        description: fields.description.trim() || null,
        address: fields.address.trim() || null,
        serviceRadiusKm: fields.serviceRadiusKm === "" ? null : fields.serviceRadiusKm,
      }, resource.data.exists);
      resource.setData({ profile: updated, exists: true });
      setFields(fieldsFromProfile(updated));
      setSaved(true);
    } catch (caught) {
      const problem = caught instanceof ApiError ? caught : new ApiError("Unable to save the worker profile");
      setErrors(profileApiErrors(problem));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div>
      <PageTitle
        eyebrow="Worker workspace"
        title="Worker profile"
        description="Manage the public information clients see when they evaluate your work."
        actions={resource.data.exists && profile.id > 0 ? <LinkButton to={`/workers/${profile.id}`} variant="secondary">View public profile</LinkButton> : undefined}
      />
      {(resource.demo || authDemo) && <DemoNotice />}
      <Alert tone="warning" title="Profile lookup limitation">
        The API does not expose a current-worker profile read endpoint. This editor starts from your signed-in identity and only loads a public profile when an owned credential supplies its authoritative worker ID. Workers without credentials cannot safely hydrate existing profile fields after refresh.
      </Alert>

      <div className="composer-grid" style={{ marginTop: 22 }}>
        <Card className="composer-main">
          <form className="stack" onSubmit={save}>
            {errors.form && <Alert tone="danger" title="Changes were not saved">{errors.form}</Alert>}
            {saved && <Alert tone="success" title="Profile saved">Your latest public profile values were returned by the API.</Alert>}
            <div>
              <span className="eyebrow">Your profile</span>
              <h2>Public details</h2>
            </div>
            <label className={`field ${errors.displayName ? "field-error" : ""}`}>
              <span className="field-label">Display name</span>
              <input maxLength={200} value={fields.displayName} onChange={(event) => setFields((current) => ({ ...current, displayName: event.target.value }))} placeholder={user.name} />
              <small>{errors.displayName || `${fields.displayName.length}/200 · Your business or professional name.`}</small>
            </label>
            <label className={`field ${errors.headline ? "field-error" : ""}`}>
              <span className="field-label">Headline</span>
              <input maxLength={255} value={fields.headline} onChange={(event) => setFields((current) => ({ ...current, headline: event.target.value }))} placeholder="A short, clear description of your service" />
              <small>{errors.headline || `${fields.headline.length}/255`}</small>
            </label>
            <label className="field">
              <span className="field-label">Description</span>
              <textarea value={fields.description} onChange={(event) => setFields((current) => ({ ...current, description: event.target.value }))} placeholder="Tell clients what you do, your experience, and what makes your work reliable." />
              <small>Public profile copy. Do not include private credentials or payment details.</small>
            </label>
            <label className={`field ${errors.address ? "field-error" : ""}`}>
              <span className="field-label">Primary service location</span>
              <input maxLength={500} value={fields.address} onChange={(event) => setFields((current) => ({ ...current, address: event.target.value }))} placeholder="Chicago, IL" />
              <small>{errors.address || `${fields.address.length}/500 · Public service area; do not enter a private home address.`}</small>
            </label>
            <label className={`field ${errors.serviceRadiusKm ? "field-error" : ""}`}>
              <span className="field-label">Service radius (km)</span>
              <input type="number" min="0" max="999999.99" step="0.01" value={fields.serviceRadiusKm} onChange={(event) => setFields((current) => ({ ...current, serviceRadiusKm: event.target.value }))} placeholder="25" />
              <small>{errors.serviceRadiusKm || "Maximum distance you travel from your primary location."}</small>
            </label>
            <div className="cluster" style={{ justifyContent: "flex-end" }}>
              <Button type="button" variant="quiet" disabled={saving} onClick={reset}>Cancel</Button>
              <Button type="submit" busy={saving} disabled={resource.demo || authDemo}>Save changes</Button>
            </div>
          </form>
        </Card>

        <Card className="composer-preview">
          <span className="eyebrow">Public preview</span>
          <div className="cluster" style={{ alignItems: "flex-start", marginBottom: 18 }}>
            <Avatar name={fields.displayName || user.name} src={user.avatarUrl} size="xl" />
            <div>
              <h2 style={{ margin: "5px 0" }}>{fields.displayName || user.name}</h2>
              {profile.verified && <span className="verified"><ShieldCheck size={13} /> Verified profile</span>}
            </div>
          </div>
          <p className="small">{fields.headline || "Your headline will appear here."}</p>
          <div className="summary-list">
            <div className="summary-item"><BadgeCheck size={17} /><div><span>Reputation</span><strong><Rating value={profile.rating} count={profile.reviewCount} compact /></strong></div></div>
            <div className="summary-item"><CheckCircle2 size={17} /><div><span>Completed jobs</span><strong>{profile.completedJobs}</strong></div></div>
            <div className="summary-item"><MapPin size={17} /><div><span>Service area</span><strong>{fields.address || "Not specified"}{fields.serviceRadiusKm ? ` · ${fields.serviceRadiusKm} km` : ""}</strong></div></div>
          </div>
          <h3>About</h3>
          <p className="small">{fields.description || "Your public description will appear here."}</p>
          {profile.recentWork.length > 0 && <><h3>Recent work</h3><div className="photo-grid">{profile.recentWork.slice(0, 4).map((work) => <img key={`${work.ticketId}-${work.image.thumb}`} src={assetUrl(work.image.thumb)} alt="" />)}</div></>}
        </Card>
      </div>
    </div>
  );
}

function validateCredentialFile(file: File | null): string | null {
  if (!file || file.size === 0) return "Choose a non-empty image file.";
  if (file.size > maxImageBytes) return "The document must be 8 MB or smaller.";
  if (!allowedImageTypes.has(file.type.toLowerCase())) return "Choose a JPEG, PNG, or WebP image.";
  return null;
}

function credentialTypeLabel(type: CredentialType) {
  return credentialTypes.find((item) => item.value === type)?.label || titleCase(type);
}

function credentialNeedsRenewal(credential: Credential) {
  return credential.status === "EXPIRED" || (credential.status === "VERIFIED" && Boolean(credential.expiresAt && credential.expiresAt < today()));
}

function resubmitUrl(credential: Credential) {
  const params = new URLSearchParams({ type: credential.type });
  if (credential.credentialNumber) params.set("number", credential.credentialNumber);
  if (credential.issuedAt) params.set("issuedAt", credential.issuedAt);
  if (credential.expiresAt) params.set("expiresAt", credential.expiresAt);
  return `/app/worker/credentials/new?${params.toString()}`;
}

async function loadAllCredentials() {
  const output: Credential[] = [];
  let page = 0;
  while (page < 100) {
    const response = await api.credentials.mine(page, 100);
    output.push(...response.items);
    if (!response.hasNext) break;
    page += 1;
  }
  return output;
}

type CredentialFilter = "ALL" | CredentialStatus;

const credentialFilters: Array<{ value: CredentialFilter; label: string }> = [
  { value: "ALL", label: "All" },
  { value: "PENDING", label: "Pending" },
  { value: "VERIFIED", label: "Verified" },
  { value: "REJECTED", label: "Rejected" },
  { value: "EXPIRED", label: "Expired" },
];

function effectiveCredentialStatus(credential: Credential): CredentialStatus {
  return credentialNeedsRenewal(credential) ? "EXPIRED" : credential.status;
}

function documentMeta(credential: Credential) {
  if (!credential.document) return "No document uploaded";
  const size = credential.document.byteSize
    ? `${(credential.document.byteSize / 1024 / 1024).toFixed(2)} MB`
    : null;
  return [credential.document.contentType?.replace("image/", "").toUpperCase(), size].filter(Boolean).join(" · ") || "Private image";
}

export function CredentialsPage() {
  const { demo: authDemo } = useAuth();
  const location = useLocation();
  const [filter, setFilter] = useState<CredentialFilter>("ALL");
  const [page, setPage] = useState(0);
  const [uploadingId, setUploadingId] = useState<number | null>(null);
  const [viewingId, setViewingId] = useState<number | null>(null);
  const [operationProblem, setOperationProblem] = useState<string | null>(null);
  const [operationSuccess, setOperationSuccess] = useState<string | null>(
    (location.state as { credentialSubmitted?: boolean } | null)?.credentialSubmitted ? "Credential submitted for review." : null,
  );
  const resource = useAsyncData<Credential[]>(loadAllCredentials, demoCredentials, []);

  const visible = useMemo(
    () => resource.data.filter((credential) => filter === "ALL" || effectiveCredentialStatus(credential) === filter),
    [resource.data, filter],
  );
  const visiblePage = useMemo(() => asPage(visible, page, 10), [visible, page]);
  const count = (status: CredentialStatus) => resource.data.filter((item) => effectiveCredentialStatus(item) === status).length;
  const demo = resource.demo || authDemo;

  const selectFilter = (next: CredentialFilter) => {
    setFilter(next);
    setPage(0);
  };

  const openDocument = async (credential: Credential) => {
    if (!credential.document) return;
    setOperationProblem(null);
    setViewingId(credential.id);
    try {
      if (demo || !credential.document.managed) {
        window.open(assetUrl(credential.document.url), "_blank", "noopener,noreferrer");
      } else {
        const privateUrl = await authenticatedBlobUrl(credential.document.url);
        const anchor = document.createElement("a");
        anchor.href = privateUrl;
        anchor.target = "_blank";
        anchor.rel = "noopener noreferrer";
        anchor.click();
        window.setTimeout(() => URL.revokeObjectURL(privateUrl), 60_000);
      }
    } catch (caught) {
      setOperationProblem(caught instanceof ApiError ? caught.message : "Unable to open the private document.");
    } finally {
      setViewingId(null);
    }
  };

  const uploadDocument = async (credential: Credential, file: File | null) => {
    const fileProblem = validateCredentialFile(file);
    if (fileProblem) {
      setOperationProblem(fileProblem);
      return;
    }
    if (demo) {
      setOperationProblem("Connect the backend before uploading a private credential document.");
      return;
    }
    setUploadingId(credential.id);
    setOperationProblem(null);
    setOperationSuccess(null);
    try {
      const updated = await api.credentials.uploadDocument(credential.id, file as File);
      resource.setData((current) => current.map((item) => item.id === updated.id ? updated : item));
      setOperationSuccess(`${credentialTypeLabel(updated.type)} document uploaded.`);
    } catch (caught) {
      const problem = caught instanceof ApiError ? caught : new ApiError("Unable to upload the credential document");
      if (problem.code === "CREDENTIAL_DOCUMENT_IMMUTABLE") {
        setOperationProblem("This document can no longer be changed because the credential is not pending review.");
        void resource.reload();
      } else {
        setOperationProblem(problem.message);
      }
    } finally {
      setUploadingId(null);
    }
  };

  if (resource.loading) return <LoadingState label="Loading credentials" />;
  if (resource.error && !resource.demo) return <ErrorState message={resource.error.message} retry={() => void resource.reload()} />;

  const pendingWithoutDocument = resource.data.filter((item) => item.status === "PENDING" && !item.document).length;
  return (
    <div>
      <PageTitle
        eyebrow="Worker workspace"
        title="Credentials"
        description="Keep regulated qualifications current and track each private review state."
        actions={<LinkButton to="/app/worker/credentials/new"><Plus size={16} /> Add credential</LinkButton>}
      />
      {demo && <DemoNotice />}
      {operationProblem && <Alert tone="danger" title="Credential action failed">{operationProblem}</Alert>}
      {operationSuccess && <Alert tone="success" title="Credential updated">{operationSuccess}</Alert>}
      {pendingWithoutDocument > 0 && (
        <Alert tone="warning" title="Document needed">
          {pendingWithoutDocument} pending {pendingWithoutDocument === 1 ? "credential has" : "credentials have"} no private document. Uploading a clear image is required before an administrator can verify it.
        </Alert>
      )}

      <div className="stats-grid" style={{ marginTop: 22 }}>
        <Card className="stat-card stat-green"><span>Verified</span><strong>{count("VERIFIED")}</strong><small>Eligible where this type is required</small></Card>
        <Card className="stat-card stat-blue"><span>Pending</span><strong>{count("PENDING")}</strong><small>Waiting for review</small></Card>
        <Card className="stat-card"><span>Rejected</span><strong>{count("REJECTED")}</strong><small>Can be corrected and resubmitted</small></Card>
        <Card className="stat-card"><span>Expired</span><strong>{count("EXPIRED")}</strong><small>Renewal details required</small></Card>
      </div>

      <div className="tabs" aria-label="Credential status filter">
        {credentialFilters.map((item) => (
          <button key={item.value} type="button" className={filter === item.value ? "active" : ""} onClick={() => selectFilter(item.value)}>
            {item.label} {item.value === "ALL" ? resource.data.length : count(item.value)}
          </button>
        ))}
      </div>

      {visiblePage.items.length ? (
        <>
          <Card className="data-card">
            <div className="table-wrap">
              <table className="data-table">
                <thead><tr><th>Credential</th><th>Issue / expiry</th><th>Private document</th><th>Status</th><th>Reviewed</th><th>Action</th></tr></thead>
                <tbody>
                  {visiblePage.items.map((credential) => {
                    const status = effectiveCredentialStatus(credential);
                    const canResubmit = status === "REJECTED" || status === "EXPIRED";
                    return (
                      <tr key={credential.id}>
                        <td>
                          <span className="table-primary">{credentialTypeLabel(credential.type)}</span>
                          <span className="table-secondary">{credential.credentialNumber || "No credential number"}</span>
                          {credential.rejectionReason && <span className="table-secondary" style={{ color: "var(--red)" }}>{credential.rejectionReason}</span>}
                        </td>
                        <td>
                          <span className="table-primary">Issued {date(credential.issuedAt)}</span>
                          <span className="table-secondary">Expires {date(credential.expiresAt)}</span>
                        </td>
                        <td>
                          {credential.document ? (
                            <Button type="button" variant="quiet" busy={viewingId === credential.id} onClick={() => void openDocument(credential)}><Eye size={15} /> View document</Button>
                          ) : <span className="muted small">No document</span>}
                          <span className="table-secondary">{documentMeta(credential)}</span>
                        </td>
                        <td><Badge value={status} /></td>
                        <td>{credential.reviewedAt ? <><span className="table-primary">{dateTime(credential.reviewedAt)}</span><span className="table-secondary">Reviewer #{credential.reviewedByUserId || "—"}</span></> : <span className="muted">—</span>}</td>
                        <td>
                          {credential.status === "PENDING" ? (
                            <label className={`button button-quiet ${uploadingId === credential.id ? "muted" : ""}`}>
                              <ImageUp size={15} /> {uploadingId === credential.id ? "Uploading…" : credential.document ? "Replace" : "Upload"}
                              <input
                                type="file"
                                accept="image/jpeg,image/png,image/webp"
                                disabled={uploadingId === credential.id}
                                style={{ display: "none" }}
                                onChange={(event) => {
                                  const file = event.target.files?.[0] || null;
                                  event.currentTarget.value = "";
                                  void uploadDocument(credential, file);
                                }}
                              />
                            </label>
                          ) : canResubmit ? (
                            <Link className="button button-quiet" to={resubmitUrl(credential)}><RefreshCw size={15} /> Resubmit</Link>
                          ) : <span className="muted small">Current</span>}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </Card>
          <Pagination page={visiblePage.page} totalPages={visiblePage.totalPages} onChange={setPage} />
        </>
      ) : (
        <EmptyState
          title={filter === "ALL" ? "No credentials submitted" : `No ${filter.toLowerCase()} credentials`}
          description={filter === "ALL" ? "Add a credential when a service category requires one." : "Choose another status or submit a new credential."}
          action={filter === "ALL" ? <LinkButton to="/app/worker/credentials/new">Add credential</LinkButton> : <Button type="button" variant="secondary" onClick={() => selectFilter("ALL")}>View all</Button>}
        />
      )}

      <Alert tone="info" title="Private by design">
        Credential images are stored privately for eligibility review. Public worker profiles show verification signals, not the underlying document or credential number.
      </Alert>
    </div>
  );
}

interface CredentialFields {
  type: CredentialType;
  credentialNumber: string;
  issuedAt: string;
  expiresAt: string;
}

type CredentialErrors = Partial<Record<keyof CredentialFields | "file" | "form", string>>;

function isCredentialType(value: string | null): value is CredentialType {
  return credentialTypes.some((item) => item.value === value);
}

function validateCredentialFields(fields: CredentialFields): CredentialErrors {
  const errors: CredentialErrors = {};
  if (!isCredentialType(fields.type)) errors.type = "Choose a supported credential type.";
  if (fields.credentialNumber.length > 100) errors.credentialNumber = "Credential number must be 100 characters or fewer.";
  if (fields.issuedAt && fields.issuedAt > today()) errors.issuedAt = "Issue date cannot be in the future.";
  if (fields.expiresAt && fields.expiresAt <= today()) errors.expiresAt = "Expiry date must be in the future.";
  if (fields.issuedAt && fields.expiresAt && fields.expiresAt < fields.issuedAt) errors.expiresAt = "Expiry date cannot be before the issue date.";
  return errors;
}

function credentialApiErrors(problem: ApiError): CredentialErrors {
  const imageCodes = new Set(["IMAGE_EMPTY", "IMAGE_TOO_LARGE", "IMAGE_TYPE_UNSUPPORTED", "IMAGE_DIMENSIONS_REJECTED", "IMAGE_CORRUPT"]);
  if (imageCodes.has(problem.code) || problem.field === "file") return { file: problem.message };
  if (problem.code === "INVALID_CREDENTIAL_DATES") return { issuedAt: problem.message, expiresAt: problem.message };
  if (problem.code === "CREDENTIAL_ALREADY_ACTIVE") return { form: "A current or pending record already exists for this credential type." };
  const fieldMap: Record<string, keyof CredentialFields> = {
    type: "type",
    credentialNumber: "credentialNumber",
    issuedAt: "issuedAt",
    expiresAt: "expiresAt",
  };
  const field = problem.field ? fieldMap[problem.field] : undefined;
  return field ? { [field]: problem.message } : { form: problem.message };
}

function initialCredentialFields(params: URLSearchParams): CredentialFields {
  const requestedType = params.get("type");
  return {
    type: isCredentialType(requestedType) ? requestedType : "ELECTRICAL_LICENSE",
    credentialNumber: params.get("number") || "",
    issuedAt: params.get("issuedAt") || "",
    expiresAt: params.get("expiresAt") || "",
  };
}

export function CredentialNewPage() {
  const navigate = useNavigate();
  const { demo: authDemo } = useAuth();
  const [searchParams] = useSearchParams();
  const resource = useAsyncData<Credential[]>(loadAllCredentials, demoCredentials, []);
  const [step, setStep] = useState<1 | 2>(1);
  const [fields, setFields] = useState<CredentialFields>(() => initialCredentialFields(searchParams));
  const [file, setFile] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [errors, setErrors] = useState<CredentialErrors>({});
  const [submitting, setSubmitting] = useState(false);
  const [pendingCredential, setPendingCredential] = useState<Credential | null>(null);
  const hydratedType = useRef<CredentialType | null>(null);
  const demo = resource.demo || authDemo;

  const existing = resource.data.find((item) => item.type === fields.type) || null;
  const existingStatus = existing ? effectiveCredentialStatus(existing) : null;
  const activeConflict = existingStatus === "PENDING" || existingStatus === "VERIFIED";

  useEffect(() => {
    if (!resource.loading && existing && hydratedType.current !== fields.type) {
      setFields((current) => ({
        ...current,
        credentialNumber: current.credentialNumber || existing.credentialNumber || "",
        issuedAt: current.issuedAt || existing.issuedAt || "",
        expiresAt: current.expiresAt || existing.expiresAt || "",
      }));
      hydratedType.current = fields.type;
    }
  }, [resource.loading, existing, fields.type]);

  useEffect(() => {
    if (!file) {
      setPreviewUrl(null);
      return;
    }
    const url = URL.createObjectURL(file);
    setPreviewUrl(url);
    return () => URL.revokeObjectURL(url);
  }, [file]);

  const selectType = (type: CredentialType) => {
    const record = resource.data.find((item) => item.type === type);
    setFields({
      type,
      credentialNumber: record?.credentialNumber || "",
      issuedAt: record?.issuedAt || "",
      expiresAt: record?.expiresAt || "",
    });
    hydratedType.current = type;
    setErrors({});
    setFile(null);
    setPendingCredential(null);
  };

  const chooseFile = (candidate: File | null) => {
    const problem = validateCredentialFile(candidate);
    if (problem) {
      setFile(null);
      setErrors((current) => ({ ...current, file: problem }));
      return;
    }
    setFile(candidate);
    setErrors((current) => ({ ...current, file: undefined, form: undefined }));
  };

  const onFileInput = (event: ChangeEvent<HTMLInputElement>) => {
    chooseFile(event.target.files?.[0] || null);
    event.currentTarget.value = "";
  };

  const onDrop = (event: DragEvent<HTMLLabelElement>) => {
    event.preventDefault();
    chooseFile(event.dataTransfer.files?.[0] || null);
  };

  const continueToDocument = (event: FormEvent) => {
    event.preventDefault();
    const nextErrors = validateCredentialFields(fields);
    if (activeConflict) nextErrors.form = existingStatus === "PENDING"
      ? "This credential is already pending review. You may upload or replace its document from the credential list."
      : "This credential is currently verified and has not expired.";
    if (Object.keys(nextErrors).length) {
      setErrors(nextErrors);
      return;
    }
    setErrors({});
    setStep(2);
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const fieldProblems = validateCredentialFields(fields);
    const fileProblem = validateCredentialFile(file);
    if (fileProblem) fieldProblems.file = fileProblem;
    if (Object.keys(fieldProblems).length) {
      setErrors(fieldProblems);
      return;
    }
    if (demo) {
      setErrors({ form: "Connect the backend before submitting a credential or private document." });
      return;
    }

    setSubmitting(true);
    setErrors({});
    let credentialForUpload = pendingCredential;
    try {
      if (!credentialForUpload) {
        credentialForUpload = await api.credentials.submit({
          type: fields.type,
          credentialNumber: fields.credentialNumber.trim() || null,
          issuedAt: fields.issuedAt || null,
          expiresAt: fields.expiresAt || null,
        });
        setPendingCredential(credentialForUpload);
      }
      await api.credentials.uploadDocument(credentialForUpload.id, file as File);
      navigate("/app/worker/credentials", { replace: true, state: { credentialSubmitted: true } });
    } catch (caught) {
      const problem = caught instanceof ApiError ? caught : new ApiError("Unable to submit the credential");
      if (credentialForUpload) {
        setErrors({
          file: problem.message,
          form: "The credential details are pending, but the private document was not uploaded. Choose a valid image and retry; do not resubmit the details.",
        });
      } else {
        const mapped = credentialApiErrors(problem);
        setErrors(mapped);
        if (mapped.issuedAt || mapped.expiresAt || mapped.credentialNumber || mapped.type) setStep(1);
      }
    } finally {
      setSubmitting(false);
    }
  };

  if (resource.loading) return <LoadingState label="Loading credential form" />;
  if (resource.error && !resource.demo) return <ErrorState message={resource.error.message} retry={() => void resource.reload()} />;

  return (
    <div style={{ maxWidth: 920 }}>
      <PageTitle
        eyebrow="Worker credentials"
        title={existingStatus === "REJECTED" || existingStatus === "EXPIRED" ? "Resubmit credential" : "Add credential"}
        description="Enter the credential details, then upload one clear private image for review."
        actions={<LinkButton to="/app/worker/credentials" variant="quiet"><ArrowLeft size={16} /> Back to credentials</LinkButton>}
      />
      {demo && <DemoNotice />}

      <div className="stepper" aria-label="Credential submission progress">
        <div className="step active"><span>{step > 1 ? <Check size={15} /> : "1"}</span>Credential details</div>
        <div className={`step ${step === 2 ? "active" : ""}`}><span>2</span>Private document</div>
      </div>

      {step === 1 ? (
        <Card className="panel">
          <form className="stack" onSubmit={continueToDocument}>
            {errors.form && <Alert tone="danger" title="Cannot continue">{errors.form}</Alert>}
            {existingStatus === "REJECTED" && <Alert tone="danger" title="Previously rejected">{existing?.rejectionReason || "Correct the credential details and upload a clearer document before resubmitting."}</Alert>}
            {existingStatus === "EXPIRED" && <Alert tone="warning" title="Renewal required">The previous credential is expired. Enter the renewed number and future expiry date.</Alert>}
            {existingStatus === "PENDING" && <Alert tone="info" title="Already pending">Details cannot be resubmitted while review is pending. You can still upload or replace its private document from the credential list.</Alert>}
            {existingStatus === "VERIFIED" && <Alert tone="success" title="Current verified credential">A verified, unexpired record already exists for this type.</Alert>}

            <label className={`field ${errors.type ? "field-error" : ""}`}>
              <span className="field-label">Credential type</span>
              <select value={fields.type} onChange={(event) => selectType(event.target.value as CredentialType)}>
                {credentialTypes.map((type) => <option key={type.value} value={type.value}>{type.label}</option>)}
              </select>
              <small>{errors.type || credentialTypes.find((item) => item.value === fields.type)?.hint}</small>
            </label>
            <label className={`field ${errors.credentialNumber ? "field-error" : ""}`}>
              <span className="field-label">Credential number <span className="muted">(optional)</span></span>
              <input maxLength={100} value={fields.credentialNumber} onChange={(event) => setFields((current) => ({ ...current, credentialNumber: event.target.value }))} placeholder="Enter the issuer-provided identifier" />
              <small>{errors.credentialNumber || `${fields.credentialNumber.length}/100`}</small>
            </label>
            <div className="form-grid">
              <label className={`field ${errors.issuedAt ? "field-error" : ""}`}>
                <span className="field-label">Issue or completion date <span className="muted">(optional)</span></span>
                <input type="date" max={today()} value={fields.issuedAt} onChange={(event) => setFields((current) => ({ ...current, issuedAt: event.target.value }))} />
                <small>{errors.issuedAt || "Cannot be in the future."}</small>
              </label>
              <label className={`field ${errors.expiresAt ? "field-error" : ""}`}>
                <span className="field-label">Expiry date <span className="muted">(optional)</span></span>
                <input type="date" min={new Date(Date.now() + 86_400_000).toISOString().slice(0, 10)} value={fields.expiresAt} onChange={(event) => setFields((current) => ({ ...current, expiresAt: event.target.value }))} />
                <small>{errors.expiresAt || "If supplied, it must be in the future."}</small>
              </label>
            </div>
            <Alert tone="info" title="Dates must match the document">Administrators compare these values with the uploaded image. Incorrect metadata can lead to rejection.</Alert>
            <div className="cluster" style={{ justifyContent: "space-between" }}>
              <LinkButton to="/app/worker/credentials" variant="quiet">Cancel</LinkButton>
              <Button type="submit" disabled={activeConflict}>Next <CalendarDays size={16} /></Button>
            </div>
          </form>
        </Card>
      ) : (
        <Card className="panel">
          <form className="stack" onSubmit={submit}>
            {errors.form && <Alert tone="danger" title={pendingCredential ? "Document upload incomplete" : "Submission failed"}>{errors.form}</Alert>}
            {pendingCredential && <Alert tone="warning" title="Details already submitted">Credential #{pendingCredential.id} is pending. Retrying now uploads only the document.</Alert>}
            <div>
              <span className="eyebrow">Step 2 of 2</span>
              <h2>Upload private document</h2>
              <p className="small">Use a clear photo or scan with the credential number and dates readable.</p>
            </div>

            <label
              className="dropzone"
              onDragOver={(event) => event.preventDefault()}
              onDrop={onDrop}
              style={errors.file ? { borderColor: "var(--red)", background: "var(--red-soft)" } : undefined}
            >
              <input type="file" accept="image/jpeg,image/png,image/webp" onChange={onFileInput} style={{ display: "none" }} />
              {previewUrl ? (
                <img src={previewUrl} alt="Selected credential document preview" style={{ borderRadius: 8, maxHeight: 260, objectFit: "contain", width: "100%" }} />
              ) : (
                <><UploadCloud size={30} /><strong>Drag and drop an image here, or click to browse</strong><span>JPEG, PNG, or WebP · maximum 8 MB</span></>
              )}
            </label>
            {file && (
              <div className="list-row">
                <div className="cluster"><FileImage size={19} color="#127a4a" /><div className="list-row-main"><strong>{file.name}</strong><span>{(file.size / 1024 / 1024).toFixed(2)} MB · {file.type || "Unknown type"}</span></div></div>
                <Button type="button" variant="quiet" onClick={() => setFile(null)}>Remove</Button>
              </div>
            )}
            {errors.file && <Alert tone="danger" title="Choose another image">{errors.file}</Alert>}

            <Alert tone="success" title="Private and normalized">
              <span className="cluster"><LockKeyhole size={16} /> The server validates image bytes, strips metadata by re-encoding, and stores the credential document privately for review.</span>
            </Alert>
            <div className="summary-list">
              <div className="summary-item"><ShieldCheck size={17} /><div><span>Credential</span><strong>{credentialTypeLabel(fields.type)}</strong></div></div>
              <div className="summary-item"><CalendarDays size={17} /><div><span>Dates</span><strong>{fields.issuedAt ? `Issued ${date(fields.issuedAt)}` : "Issue date not supplied"}{fields.expiresAt ? ` · Expires ${date(fields.expiresAt)}` : ""}</strong></div></div>
              <div className="summary-item"><ImageUp size={17} /><div><span>Accepted upload</span><strong>JPEG, PNG, or WebP up to 8 MB</strong></div></div>
            </div>
            <div className="cluster" style={{ justifyContent: "space-between" }}>
              <Button type="button" variant="quiet" disabled={submitting || Boolean(pendingCredential)} onClick={() => setStep(1)}>Back</Button>
              <Button type="submit" busy={submitting} disabled={!file || demo}>{pendingCredential ? "Retry document upload" : "Submit for review"}</Button>
            </div>
          </form>
        </Card>
      )}
    </div>
  );
}
