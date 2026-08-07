import {
  BadgeDollarSign,
  BriefcaseBusiness,
  ClipboardCheck,
  FileCheck2,
  FileText,
  Gauge,
  HandCoins,
  Layers3,
  LayoutDashboard,
  Menu,
  Search,
  Settings,
  ShieldCheck,
  Star,
  Tags,
  UserRound,
  UsersRound,
  WalletCards,
  X,
} from "lucide-react";
import { useEffect, useState, type PropsWithChildren } from "react";
import { Link, NavLink, Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { Avatar, Brand, LinkButton } from "./ui";

export function PublicLayout() {
  const { user } = useAuth();
  const [open, setOpen] = useState(false);
  const location = useLocation();

  useEffect(() => {
    setOpen(false);
  }, [location.pathname]);

  return (
    <div className="site-shell">
      <header className="public-header">
        <div className="container header-inner">
          <Brand />
          <button className="menu-toggle" type="button" onClick={() => setOpen((value) => !value)} aria-label="Toggle navigation">
            {open ? <X /> : <Menu />}
          </button>
          <nav className={open ? "public-nav is-open" : "public-nav"}>
            <NavLink to="/marketplace">Discover</NavLink>
            <NavLink to="/workers">Workers</NavLink>
            <NavLink to="/how-it-works">How it works</NavLink>
          </nav>
          <div className={open ? "header-actions is-open" : "header-actions"}>
            {user ? (
              <>
                <Link className="user-link" to={user.role === "ADMIN" ? "/admin" : "/app/client"}>
                  <Avatar name={user.name} src={user.avatarUrl} size="sm" />
                  <span>{user.name}</span>
                </Link>
                <LinkButton to={user.role === "ADMIN" ? "/admin" : "/app/client"}>My work</LinkButton>
              </>
            ) : (
              <>
                <Link className="text-link" to="/login">Sign in</Link>
                <LinkButton to="/register">Create account</LinkButton>
              </>
            )}
          </div>
        </div>
      </header>
      <main><Outlet /></main>
      <PublicFooter />
    </div>
  );
}

export function PublicFooter() {
  return (
    <footer className="public-footer">
      <div className="container footer-grid">
        <div><Brand /><p>Trusted local services, with clear marketplace states.</p></div>
        <div><strong>Marketplace</strong><Link to="/marketplace">Discover</Link><Link to="/workers">Workers</Link><Link to="/how-it-works">How it works</Link></div>
        <div><strong>Legal</strong><Link to="/terms">Terms</Link><Link to="/privacy">Privacy</Link></div>
        <div><strong>Local demo</strong><span>Chicago, Illinois</span><span>USD</span></div>
      </div>
      <div className="container footer-bottom">© 2026 Service Marketplace. All rights reserved.</div>
    </footer>
  );
}

export function ProtectedRoute({ admin = false }: { admin?: boolean }) {
  const { user, loading } = useAuth();
  const location = useLocation();
  if (loading) return <div className="route-loader">Loading your workspace…</div>;
  if (!user) return <Navigate to="/login" replace state={{ from: `${location.pathname}${location.search}${location.hash}` }} />;
  if (admin && user.role !== "ADMIN") return <Navigate to="/forbidden" replace />;
  return <Outlet />;
}

type NavItem = { to: string; label: string; icon: typeof LayoutDashboard; end?: boolean };

const clientNav: NavItem[] = [
  { to: "/app/client", label: "Dashboard", icon: LayoutDashboard, end: true },
  { to: "/app/client/requests", label: "My requests", icon: FileText },
  { to: "/app/client/applications", label: "Service requests", icon: ClipboardCheck },
  { to: "/app/client/engagements", label: "Engagements", icon: BriefcaseBusiness },
  { to: "/app/client/refunds", label: "Refunds", icon: HandCoins },
  { to: "/app/client/reputation", label: "Reputation", icon: Star },
];

const workerNav: NavItem[] = [
  { to: "/app/worker", label: "Dashboard", icon: LayoutDashboard, end: true },
  { to: "/app/worker/offers", label: "My services", icon: FileText },
  { to: "/app/worker/proposals", label: "Proposals", icon: ClipboardCheck },
  { to: "/app/worker/engagements", label: "Engagements", icon: BriefcaseBusiness },
  { to: "/app/worker/profile", label: "Worker profile", icon: UserRound },
  { to: "/app/worker/credentials", label: "Credentials", icon: ShieldCheck },
  { to: "/app/worker/earnings", label: "Earnings", icon: BadgeDollarSign },
  { to: "/app/worker/reputation", label: "Reputation", icon: Star },
];

const adminNav: NavItem[] = [
  { to: "/admin", label: "Overview", icon: Gauge, end: true },
  { to: "/admin/credentials", label: "Credential review", icon: FileCheck2 },
  { to: "/admin/disputes", label: "Disputes", icon: ShieldCheck },
  { to: "/admin/finance", label: "Finance", icon: WalletCards },
  { to: "/admin/categories", label: "Categories", icon: Tags },
  { to: "/admin/audit", label: "Audit lookup", icon: Search },
];

export function WorkspaceLayout({ mode }: { mode: "client" | "worker" | "admin" }) {
  const { user, demo, logout } = useAuth();
  const location = useLocation();
  const [mobileOpen, setMobileOpen] = useState(false);
  const nav = mode === "client" ? clientNav : mode === "worker" ? workerNav : adminNav;

  useEffect(() => {
    setMobileOpen(false);
  }, [location.pathname]);

  if (!user) return null;

  const modeLabel = mode === "admin" ? "Admin" : mode === "client" ? "Client" : "Worker";

  return (
    <div className="workspace-shell">
      <header className="workspace-topbar">
        <Brand />
        {mode !== "admin" && (
          <div className="workspace-switcher" aria-label="Workspace switcher">
            <NavLink to="/app/client">Client</NavLink>
            <NavLink to="/app/worker">Worker</NavLink>
          </div>
        )}
        <div className="topbar-user">
          {demo && <span className="demo-pill">Demo</span>}
          <Link to="/account"><Avatar name={user.name} src={user.avatarUrl} size="sm" /><span>{user.name}</span></Link>
          <button className="menu-toggle" type="button" onClick={() => setMobileOpen((value) => !value)}>{mobileOpen ? <X /> : <Menu />}</button>
        </div>
      </header>
      <aside className={mobileOpen ? "workspace-sidebar is-open" : "workspace-sidebar"}>
        <div className="workspace-label"><span>{modeLabel}</span> workspace</div>
        <nav>
          {nav.map(({ to, label, icon: Icon, end }) => (
            <NavLink key={to} to={to} end={end}><Icon size={18} /><span>{label}</span></NavLink>
          ))}
        </nav>
        <div className="sidebar-footer">
          <NavLink to="/account"><Settings size={18} /> Account</NavLink>
          <button type="button" onClick={() => void logout()}>Sign out</button>
        </div>
      </aside>
      <main className="workspace-main"><Outlet /></main>
      <nav className="mobile-workspace-nav">
        {nav.slice(0, 4).map(({ to, label, icon: Icon, end }) => (
          <NavLink key={to} to={to} end={end}><Icon size={19} /><span>{label.split(" ")[0]}</span></NavLink>
        ))}
        <button type="button" onClick={() => setMobileOpen(true)}><Layers3 size={19} /><span>More</span></button>
      </nav>
    </div>
  );
}

export function SimplePage({ children }: PropsWithChildren) {
  return <div className="container page-section">{children}</div>;
}
