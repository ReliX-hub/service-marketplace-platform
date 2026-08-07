import { ArrowRight, BriefcaseBusiness, Plus, ShieldCheck } from "lucide-react";
import { Link } from "react-router-dom";
import { api } from "../../lib/api";
import { asPage, demoApplications, demoCredentials, demoEngagements, demoTickets } from "../../data/demo";
import { dateTime, money } from "../../lib/format";
import { useAsyncData } from "../../hooks/useAsyncData";
import type { Application, Credential, Engagement, PageResponse, TicketSummary } from "../../types";
import { Badge, Button, Card, DemoNotice, LinkButton, LoadingState, PageTitle, StatCard } from "../../components/ui";

interface DashboardData {
  listings: PageResponse<TicketSummary>;
  applications: PageResponse<Application>;
  engagements: PageResponse<Engagement>;
  credentials?: PageResponse<Credential>;
}

function EngagementRows({ items, mode }: { items: Engagement[]; mode: "client" | "worker" }) {
  return (
    <Card className="data-card">
      <div className="data-card-header">
        <h2>Active engagements</h2>
        <Link className="text-link" to={`/app/${mode}/engagements`}>View all <ArrowRight size={14} /></Link>
      </div>
      <div className="table-wrap">
        <table className="data-table">
          <thead><tr><th>Engagement</th><th>{mode === "client" ? "Provider" : "Client"}</th><th>Amount</th><th>Schedule</th><th>Status</th><th /></tr></thead>
          <tbody>
            {items.slice(0, 5).map((item) => (
              <tr key={item.id}>
                <td><span className="table-primary">ENG-{item.id}</span><span className="table-secondary">Ticket #{item.ticketId}</span></td>
                <td>{mode === "client" ? item.workerDisplayName : item.clientName}</td>
                <td>{money(item.amount)}</td>
                <td>{dateTime(item.scheduledStart)}</td>
                <td><Badge value={item.status} /></td>
                <td><LinkButton to={`/app/${mode}/engagements/${item.id}`} variant="quiet">Open</LinkButton></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </Card>
  );
}

function QuickPanel({ mode, credentials }: { mode: "client" | "worker"; credentials?: Credential[] }) {
  return (
    <Card className="panel">
      <h2>Next steps</h2>
      {mode === "client" ? (
        <div className="stack">
          <div className="list-row"><div className="list-row-main"><strong>Post a task</strong><span>Describe what you need and compare proposals.</span></div><Link to="/app/client/requests/new"><Plus size={18} /></Link></div>
          <div className="list-row"><div className="list-row-main"><strong>Browse services</strong><span>Respond to an open service offer.</span></div><Link to="/marketplace?kind=OFFER"><ArrowRight size={18} /></Link></div>
          <div className="list-row"><div className="list-row-main"><strong>Review active work</strong><span>Funding, work, and delivery status in one place.</span></div><Link to="/app/client/engagements"><ArrowRight size={18} /></Link></div>
        </div>
      ) : (
        <div className="stack">
          <div className="list-row"><div className="list-row-main"><strong>Create a service</strong><span>Publish an OFFER for clients to discover.</span></div><Link to="/app/worker/offers/new"><Plus size={18} /></Link></div>
          <div className="list-row"><div className="list-row-main"><strong>Browse client requests</strong><span>Find open work that fits your services.</span></div><Link to="/marketplace?kind=REQUEST"><ArrowRight size={18} /></Link></div>
          <div className="list-row"><div className="list-row-main"><strong>Credential readiness</strong><span>{credentials?.filter((item) => item.status === "VERIFIED").length ?? 0} verified · {credentials?.filter((item) => item.status === "PENDING").length ?? 0} pending</span></div><Link to="/app/worker/credentials"><ShieldCheck size={18} /></Link></div>
        </div>
      )}
    </Card>
  );
}

export function ClientDashboardPage() {
  const fallback: DashboardData = {
    listings: asPage(demoTickets.filter((item) => item.kind === "REQUEST")),
    applications: asPage(demoApplications.filter((item) => item.ticketKind === "OFFER")),
    engagements: asPage(demoEngagements),
  };
  const resource = useAsyncData(
    async () => {
      const [listings, applications, engagements] = await Promise.all([
        api.tickets.mine({ kind: "REQUEST", size: 100 }),
        api.applications.mine(undefined, 0, 100),
        api.engagements.list("client", undefined, 0, 100),
      ]);
      return { listings, applications: { ...applications, items: applications.items.filter((item) => item.ticketKind === "OFFER") }, engagements };
    },
    fallback,
    [],
  );

  if (resource.loading && !resource.data) return <LoadingState />;
  const active = resource.data.engagements.items.filter((item) => !["COMPLETED", "CANCELLED", "REFUNDED"].includes(item.status));

  return (
    <div>
      {resource.demo && <DemoNotice />}
      <PageTitle eyebrow="Client workspace" title="Good work starts with a clear request." description="Track your tasks, service requests, funding, and active work." actions={<LinkButton to="/app/client/requests/new"><Plus size={16} /> Post a task</LinkButton>} />
      <div className="stats-grid">
        <StatCard label="My requests" value={resource.data.listings.totalElements} hint="All listing states" tone="violet" />
        <StatCard label="Service requests sent" value={resource.data.applications.totalElements} hint="Responses to OFFER listings" tone="blue" />
        <StatCard label="Active engagements" value={active.length} hint="Accepted through disputed" tone="green" />
        <StatCard label="Awaiting approval" value={active.filter((item) => item.status === "DELIVERED").length} hint="Delivered work to review" />
      </div>
      <div className="content-grid">
        <EngagementRows items={active} mode="client" />
        <QuickPanel mode="client" />
      </div>
    </div>
  );
}

export function WorkerDashboardPage() {
  const fallback: DashboardData = {
    listings: asPage(demoTickets.filter((item) => item.kind === "OFFER")),
    applications: asPage(demoApplications.filter((item) => item.ticketKind === "REQUEST")),
    engagements: asPage(demoEngagements),
    credentials: asPage(demoCredentials),
  };
  const resource = useAsyncData(
    async () => {
      const [listings, applications, engagements, credentials] = await Promise.all([
        api.tickets.mine({ kind: "OFFER", size: 100 }),
        api.applications.mine(undefined, 0, 100),
        api.engagements.list("worker", undefined, 0, 100),
        api.credentials.mine(0, 100),
      ]);
      return { listings, applications: { ...applications, items: applications.items.filter((item) => item.ticketKind === "REQUEST") }, engagements, credentials };
    },
    fallback,
    [],
  );

  const active = resource.data.engagements.items.filter((item) => !["COMPLETED", "CANCELLED", "REFUNDED"].includes(item.status));
  const readyToStart = active.filter((item) => item.status === "FUNDED").length;

  return (
    <div>
      {resource.demo && <DemoNotice />}
      <PageTitle eyebrow="Worker workspace" title="Your services, proposals, and work." description="See what needs attention and move each engagement forward." actions={<LinkButton to="/app/worker/offers/new"><Plus size={16} /> Create service</LinkButton>} />
      <div className="stats-grid">
        <StatCard label="My services" value={resource.data.listings.totalElements} hint="OFFER listings" tone="blue" />
        <StatCard label="Proposals submitted" value={resource.data.applications.totalElements} hint="Responses to REQUEST listings" tone="violet" />
        <StatCard label="Ready to start" value={readyToStart} hint="Client funding confirmed" tone="green" />
        <StatCard label="In progress" value={active.filter((item) => item.status === "IN_PROGRESS").length} hint="Evidence can be added" />
      </div>
      <div className="content-grid">
        <EngagementRows items={active} mode="worker" />
        <QuickPanel mode="worker" credentials={resource.data.credentials?.items} />
      </div>
    </div>
  );
}
