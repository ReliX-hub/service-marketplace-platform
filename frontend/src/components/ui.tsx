import {
  AlertCircle,
  ArrowRight,
  Check,
  ChevronLeft,
  ChevronRight,
  CircleDashed,
  Inbox,
  LoaderCircle,
  MapPin,
  RefreshCw,
  Search,
  ShieldCheck,
  Star,
  X,
} from "lucide-react";
import type { ButtonHTMLAttributes, HTMLAttributes, PropsWithChildren, ReactNode } from "react";
import { useEffect, useId, useRef } from "react";
import { Link } from "react-router-dom";
import { assetUrl } from "../lib/api";
import { initials, locationLabels, ticketPrice, titleCase } from "../lib/format";
import type { TicketSummary, WorkerProfile } from "../types";

export function Brand({ compact = false }: { compact?: boolean }) {
  return (
    <Link className="brand" to="/" aria-label="Service Marketplace home">
      <span className="brand-mark" aria-hidden="true">
        <span />
      </span>
      {!compact && <span>Service Marketplace</span>}
    </Link>
  );
}

type ButtonVariant = "primary" | "secondary" | "quiet" | "danger";

export function Button({
  variant = "primary",
  busy,
  children,
  className = "",
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: ButtonVariant; busy?: boolean }) {
  return (
    <button className={`button button-${variant} ${className}`} disabled={busy || props.disabled} {...props}>
      {busy && <LoaderCircle className="spin" size={16} />}
      {children}
    </button>
  );
}

export function LinkButton({
  to,
  variant = "primary",
  children,
  className = "",
}: PropsWithChildren<{ to: string; variant?: ButtonVariant; className?: string }>) {
  return (
    <Link className={`button button-${variant} ${className}`} to={to}>
      {children}
    </Link>
  );
}

export function Card({ children, className = "", ...props }: PropsWithChildren<HTMLAttributes<HTMLDivElement>>) {
  return (
    <div className={`card ${className}`} {...props}>
      {children}
    </div>
  );
}

export function Badge({ value, className = "" }: { value: string; className?: string }) {
  const key = value.toLowerCase().replace(/_/g, "-");
  return <span className={`badge badge-${key} ${className}`}>{titleCase(value)}</span>;
}

export function Avatar({ name, src, size = "md" }: { name: string; src?: string | null; size?: "sm" | "md" | "lg" | "xl" }) {
  return src ? (
    <img className={`avatar avatar-${size}`} src={assetUrl(src)} alt="" />
  ) : (
    <span className={`avatar avatar-${size} avatar-initials`} aria-hidden="true">
      {initials(name)}
    </span>
  );
}

export function Rating({ value, count, compact = false }: { value: string | number; count?: number; compact?: boolean }) {
  return (
    <span className="rating" aria-label={`${value} out of 5 stars`}>
      <Star size={compact ? 13 : 15} fill="currentColor" />
      <strong>{Number(value).toFixed(1)}</strong>
      {count !== undefined && <span>({count})</span>}
    </span>
  );
}

export function Alert({
  tone = "info",
  title,
  children,
  action,
}: PropsWithChildren<{ tone?: "info" | "success" | "warning" | "danger"; title?: string; action?: ReactNode }>) {
  const Icon = tone === "success" ? Check : tone === "warning" || tone === "danger" ? AlertCircle : ShieldCheck;
  return (
    <div className={`alert alert-${tone}`} role={tone === "danger" ? "alert" : undefined}>
      <Icon size={18} />
      <div>
        {title && <strong>{title}</strong>}
        {children && <div>{children}</div>}
      </div>
      {action && <div className="alert-action">{action}</div>}
    </div>
  );
}

export function DemoNotice() {
  return (
    <div className="demo-notice">
      <CircleDashed size={15} />
      Demo data is shown because the API is offline. Start the backend to use live marketplace data.
    </div>
  );
}

export function PageTitle({ eyebrow, title, description, actions }: { eyebrow?: string; title: string; description?: string; actions?: ReactNode }) {
  return (
    <div className="page-title">
      <div>
        {eyebrow && <span className="eyebrow">{eyebrow}</span>}
        <h1>{title}</h1>
        {description && <p>{description}</p>}
      </div>
      {actions && <div className="page-actions">{actions}</div>}
    </div>
  );
}

export function EmptyState({
  title,
  description,
  action,
}: {
  title: string;
  description: string;
  action?: ReactNode;
}) {
  return (
    <div className="empty-state">
      <span className="empty-icon"><Inbox size={24} /></span>
      <h3>{title}</h3>
      <p>{description}</p>
      {action}
    </div>
  );
}

export function LoadingState({ label = "Loading" }: { label?: string }) {
  return (
    <div className="loading-state">
      <LoaderCircle className="spin" size={24} />
      <span>{label}</span>
    </div>
  );
}

export function ErrorState({ message, retry }: { message: string; retry?: () => void }) {
  return (
    <div className="empty-state error-state" role="alert">
      <span className="empty-icon"><AlertCircle size={24} /></span>
      <h3>Something went wrong</h3>
      <p>{message}</p>
      {retry && <Button variant="secondary" onClick={retry}><RefreshCw size={15} /> Try again</Button>}
    </div>
  );
}

export function TicketCard({ ticket, compact = false }: { ticket: TicketSummary; compact?: boolean }) {
  return (
    <article className={`ticket-card ${compact ? "ticket-card-compact" : ""}`}>
      <Link className="ticket-image" to={`/tickets/${ticket.id}`}>
        {ticket.coverImage ? <img src={assetUrl(ticket.coverImage.thumb)} alt="" /> : <span className="image-placeholder" />}
        <Badge value={ticket.kind} />
      </Link>
      <div className="ticket-body">
        <div className="ticket-meta-top">
          <span>{ticket.category.name}</span>
          <span>{ticket.createdAt ? "Recently posted" : ""}</span>
        </div>
        <Link className="ticket-title" to={`/tickets/${ticket.id}`}>{ticket.title}</Link>
        {ticket.worker && <span className="muted small">{ticket.worker.displayName}</span>}
        <strong className="ticket-price">{ticketPrice(ticket)}</strong>
        <div className="ticket-meta">
          <span><MapPin size={13} /> {locationLabels[ticket.locationMode]}{ticket.city ? ` · ${ticket.city}` : ""}</span>
          <span>{ticket.applicationCount} {ticket.applicationCount === 1 ? "response" : "responses"}</span>
        </div>
      </div>
    </article>
  );
}

export function WorkerCard({ worker }: { worker: WorkerProfile }) {
  return (
    <Card className="worker-card">
      <div className="worker-card-head">
        <Avatar name={worker.displayName} size="lg" />
        <div>
          <Link className="ticket-title" to={`/workers/${worker.id}`}>{worker.displayName}</Link>
          {worker.verified && <span className="verified"><ShieldCheck size={13} /> Verified profile</span>}
        </div>
      </div>
      <p>{worker.headline || "Local service professional"}</p>
      <div className="worker-stats">
        <Rating value={worker.rating} count={worker.reviewCount} />
        <span>{worker.completedJobs} jobs</span>
      </div>
      <div className="worker-location"><MapPin size={14} /> {worker.address || "Chicago, IL"}</div>
      <LinkButton to={`/workers/${worker.id}`} variant="secondary">View profile <ArrowRight size={15} /></LinkButton>
    </Card>
  );
}

export function StatCard({ label, value, hint, tone = "default" }: { label: string; value: ReactNode; hint?: string; tone?: "default" | "green" | "blue" | "violet" }) {
  return (
    <Card className={`stat-card stat-${tone}`}>
      <span>{label}</span>
      <strong>{value}</strong>
      {hint && <small>{hint}</small>}
    </Card>
  );
}

export function Pagination({ page, totalPages, onChange }: { page: number; totalPages: number; onChange: (page: number) => void }) {
  if (totalPages <= 1) return null;
  return (
    <div className="pagination" aria-label="Pagination">
      <Button variant="quiet" disabled={page <= 0} onClick={() => onChange(page - 1)}><ChevronLeft size={16} /> Prev</Button>
      <span>Page {page + 1} of {totalPages}</span>
      <Button variant="quiet" disabled={page >= totalPages - 1} onClick={() => onChange(page + 1)}>Next <ChevronRight size={16} /></Button>
    </div>
  );
}

export function SearchField({ value, onChange, placeholder = "Search" }: { value: string; onChange: (value: string) => void; placeholder?: string }) {
  return (
    <label className="search-field">
      <Search size={17} />
      <input value={value} onChange={(event) => onChange(event.target.value)} placeholder={placeholder} />
      {value && <button type="button" onClick={() => onChange("")} aria-label="Clear search"><X size={15} /></button>}
    </label>
  );
}

export function Modal({ title, open, onClose, children, footer }: PropsWithChildren<{ title: string; open: boolean; onClose: () => void; footer?: ReactNode }>) {
  const titleId = useId();
  const dialogRef = useRef<HTMLElement>(null);
  useEffect(() => {
    if (!open) return;
    const previous = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const oldOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    const frame = requestAnimationFrame(() => dialogRef.current?.focus());
    const keydown = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
      if (event.key !== "Tab" || !dialogRef.current) return;
      const focusable = Array.from(dialogRef.current.querySelectorAll<HTMLElement>('button:not([disabled]), a[href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'));
      if (focusable.length === 0) { event.preventDefault(); dialogRef.current.focus(); return; }
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
    };
    document.addEventListener("keydown", keydown);
    return () => {
      cancelAnimationFrame(frame);
      document.removeEventListener("keydown", keydown);
      document.body.style.overflow = oldOverflow;
      previous?.focus();
    };
  }, [open, onClose]);
  if (!open) return null;
  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={onClose}>
      <section ref={dialogRef} tabIndex={-1} className="modal" role="dialog" aria-modal="true" aria-labelledby={titleId} onMouseDown={(event) => event.stopPropagation()}>
        <header><h2 id={titleId}>{title}</h2><button type="button" onClick={onClose} aria-label="Close"><X size={18} /></button></header>
        <div className="modal-body">{children}</div>
        {footer && <footer>{footer}</footer>}
      </section>
    </div>
  );
}
