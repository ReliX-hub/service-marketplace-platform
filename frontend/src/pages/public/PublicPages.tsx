import {
  ArrowRight,
  BadgeCheck,
  BriefcaseBusiness,
  CalendarDays,
  CheckCircle2,
  ChevronRight,
  CircleDollarSign,
  ClipboardCheck,
  Clock3,
  FileCheck2,
  HeartHandshake,
  Laptop,
  MapPin,
  Search,
  ShieldCheck,
  SlidersHorizontal,
  Sparkles,
  Star,
  Truck,
  UserRound,
  UsersRound,
  Wrench,
  Zap,
  type LucideIcon,
} from "lucide-react";
import { useMemo, useState, type CSSProperties, type FormEvent, type ReactNode } from "react";
import { Link, useLocation, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { useAuth } from "../../auth/AuthContext";
import { SimplePage } from "../../components/layout";
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
  Modal,
  PageTitle,
  Pagination,
  Rating,
  SearchField,
  TicketCard,
  WorkerCard,
} from "../../components/ui";
import {
  asPage,
  demoCategories,
  demoReviews,
  demoTicketDetails,
  demoTickets,
  demoWorkers,
} from "../../data/demo";
import { useAsyncData } from "../../hooks/useAsyncData";
import { api, ApiError, assetUrl } from "../../lib/api";
import { date, dateTime, locationLabels, money, pricingLabels, ticketPrice, titleCase } from "../../lib/format";
import type {
  Application,
  Category,
  LocationMode,
  PageResponse,
  PricingMode,
  Review,
  TicketDetail,
  TicketKind,
  TicketSummary,
  WorkerProfile,
} from "../../types";

const pageStyles: Record<string, CSSProperties> = {
  hero: {
    background: "#f5f2eb",
    borderBottom: "1px solid #e5e1d8",
    overflow: "hidden",
    padding: "clamp(52px, 8vw, 104px) 0",
  },
  heroGrid: {
    alignItems: "center",
    display: "grid",
    gap: "clamp(30px, 6vw, 72px)",
    gridTemplateColumns: "repeat(auto-fit, minmax(min(100%, 360px), 1fr))",
  },
  heroTitle: { fontSize: "clamp(3rem, 7vw, 6.6rem)", lineHeight: 0.94, maxWidth: 760 },
  heroCopy: { fontSize: "1.02rem", maxWidth: 610 },
  heroSearch: {
    alignItems: "center",
    background: "white",
    border: "1px solid #d8ddd8",
    borderRadius: 14,
    boxShadow: "0 16px 44px rgb(17 24 39 / 10%)",
    display: "grid",
    gap: 10,
    gridTemplateColumns: "minmax(150px, 1.5fr) minmax(130px, 1fr) auto",
    padding: 10,
  },
  heroField: { alignItems: "center", display: "flex", gap: 8, minWidth: 0, padding: "0 9px" },
  heroInput: { background: "transparent", border: 0, minHeight: 42, minWidth: 0, outline: 0, width: "100%" },
  collage: { display: "grid", gap: 12, gridTemplateColumns: "1.2fr .8fr", minHeight: 430 },
  collageMain: { borderRadius: 22, height: "100%", objectFit: "cover", width: "100%" },
  collageSide: { display: "grid", gap: 12, gridTemplateRows: "1fr 1fr" },
  collageImage: { borderRadius: 18, height: "100%", minHeight: 0, objectFit: "cover", width: "100%" },
  categoryGrid: { display: "grid", gap: 14, gridTemplateColumns: "repeat(auto-fit, minmax(165px, 1fr))" },
  categoryCard: { display: "grid", gap: 12, minHeight: 150, padding: 20 },
  iconTile: {
    alignItems: "center",
    background: "#eaf5ef",
    borderRadius: 12,
    color: "#127a4a",
    display: "flex",
    height: 42,
    justifyContent: "center",
    width: 42,
  },
  pathwayCard: { display: "grid", gap: 14, padding: 26 },
  profileHero: { alignItems: "center", display: "flex", flexWrap: "wrap", gap: 20, padding: 26 },
  recentGrid: { display: "grid", gap: 12, gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))" },
  recentImage: { aspectRatio: "4 / 3", borderRadius: 10, objectFit: "cover", width: "100%" },
  lifecycle: { display: "grid", gap: 10, marginTop: 18 },
  lifecycleStep: {
    alignItems: "center",
    background: "#f8f8f5",
    border: "1px solid #e5e7eb",
    borderRadius: 10,
    display: "grid",
    gap: 12,
    gridTemplateColumns: "34px 1fr",
    padding: 12,
  },
  lifecycleNumber: {
    alignItems: "center",
    background: "#127a4a",
    borderRadius: "50%",
    color: "white",
    display: "flex",
    fontSize: ".75rem",
    fontWeight: 800,
    height: 28,
    justifyContent: "center",
    width: 28,
  },
};

const iconMap: Record<string, LucideIcon> = {
  heart: HeartHandshake,
  laptop: Laptop,
  sparkles: Sparkles,
  truck: Truck,
  wrench: Wrench,
  zap: Zap,
};

function CategoryIcon({ category }: { category: Category }) {
  const Icon = iconMap[category.icon || ""] || BriefcaseBusiness;
  return <Icon size={21} />;
}

function DatasetNotice({ demo }: { demo: boolean }) {
  return demo ? <DemoNotice /> : null;
}

function fatalError(error: ApiError | null, demo: boolean) {
  return Boolean(error && !demo);
}

function priceBounds(ticket: TicketSummary) {
  if (ticket.pricingMode === "FIXED") {
    const value = Number(ticket.price || 0);
    return [value, value] as const;
  }
  if (ticket.pricingMode === "BUDGET_RANGE") {
    return [Number(ticket.budgetMin || 0), Number(ticket.budgetMax || 0)] as const;
  }
  return [null, null] as const;
}

function toLocalDateTime(value: string | null | undefined) {
  if (!value) return "";
  const parsed = new Date(value);
  const offset = parsed.getTimezoneOffset() * 60_000;
  return new Date(parsed.getTime() - offset).toISOString().slice(0, 16);
}

function toIso(value: string) {
  return value ? new Date(value).toISOString() : undefined;
}

function responseAmount(ticket: TicketDetail | null) {
  if (!ticket) return "";
  if (ticket.pricingMode === "FIXED") return ticket.price || "";
  if (ticket.pricingMode === "BUDGET_RANGE") return ticket.budgetMin || "";
  return "";
}

function humanCredential(value: Category["requiredCredential"]) {
  return value ? titleCase(value) : "";
}

interface HomeData {
  categories: Category[];
  offers: TicketSummary[];
  requests: TicketSummary[];
  workers: WorkerProfile[];
}

const homeFallback: HomeData = {
  categories: demoCategories,
  offers: demoTickets.filter((ticket) => ticket.kind === "OFFER").slice(0, 4),
  requests: demoTickets.filter((ticket) => ticket.kind === "REQUEST").slice(0, 4),
  workers: demoWorkers.slice(0, 3),
};

export function HomePage() {
  const navigate = useNavigate();
  const [query, setQuery] = useState("");
  const [city, setCity] = useState("Chicago");
  const home = useAsyncData<HomeData>(
    async () => {
      const [categories, offers, requests, workers] = await Promise.all([
        api.categories.list(),
        api.tickets.list({ kind: "OFFER", page: 0, size: 4, sort: "createdAt,desc" }),
        api.tickets.list({ kind: "REQUEST", page: 0, size: 4, sort: "createdAt,desc" }),
        api.workers.list(0, 3, true),
      ]);
      return { categories, offers: offers.items, requests: requests.items, workers: workers.items };
    },
    homeFallback,
    [],
  );

  const search = (event: FormEvent) => {
    event.preventDefault();
    const params = new URLSearchParams({ kind: "OFFER" });
    if (query.trim()) params.set("q", query.trim());
    if (city.trim()) params.set("city", city.trim());
    navigate(`/marketplace?${params.toString()}`);
  };

  return (
    <>
      <section style={pageStyles.hero}>
        <div className="container" style={pageStyles.heroGrid}>
          <div>
            <span className="eyebrow">Local help, clearer outcomes</span>
            <h1 style={pageStyles.heroTitle}>Find trusted help—or your next job.</h1>
            <p style={pageStyles.heroCopy}>
              Compare local service offers, respond to open requests, and keep every engagement in one clear workflow.
            </p>
            <form onSubmit={search} style={pageStyles.heroSearch} aria-label="Search the marketplace">
              <label style={pageStyles.heroField}>
                <Search size={18} aria-hidden="true" />
                <input
                  style={pageStyles.heroInput}
                  value={query}
                  onChange={(event) => setQuery(event.target.value)}
                  placeholder="What help do you need?"
                  aria-label="Service or task"
                />
              </label>
              <label style={pageStyles.heroField}>
                <MapPin size={18} aria-hidden="true" />
                <input
                  style={pageStyles.heroInput}
                  value={city}
                  onChange={(event) => setCity(event.target.value)}
                  placeholder="City"
                  aria-label="City"
                />
              </label>
              <Button type="submit">Search <ArrowRight size={16} /></Button>
            </form>
          </div>
          <div style={pageStyles.collage} aria-hidden="true">
            <img style={pageStyles.collageMain} src="/images/home-cleaning.jpg" alt="" />
            <div style={pageStyles.collageSide}>
              <img style={pageStyles.collageImage} src="/images/moving-help.jpg" alt="" />
              <img style={pageStyles.collageImage} src="/images/tech-support.jpg" alt="" />
            </div>
          </div>
        </div>
      </section>

      <SimplePage>
        <DatasetNotice demo={home.demo} />
        {home.loading ? (
          <LoadingState label="Loading the marketplace" />
        ) : fatalError(home.error, home.demo) ? (
          <ErrorState message={home.error?.message || "Unable to load the marketplace."} retry={() => void home.reload()} />
        ) : (
          <div className="stack" style={{ gap: 64 }}>
            <section>
              <div className="section-heading">
                <div><span className="eyebrow">Browse by category</span><h2>Start with the work you need</h2></div>
                <Link className="text-link" to="/marketplace">See all listings <ArrowRight size={14} /></Link>
              </div>
              <div style={pageStyles.categoryGrid}>
                {home.data.categories.map((category) => (
                  <Link key={category.id} to={`/categories/${category.code}`}>
                    <Card style={pageStyles.categoryCard}>
                      <span style={pageStyles.iconTile}><CategoryIcon category={category} /></span>
                      <div><strong>{category.name}</strong><p className="small" style={{ margin: "5px 0 0" }}>{category.description || "Explore local listings"}</p></div>
                    </Card>
                  </Link>
                ))}
              </div>
            </section>

            <section className="content-grid content-grid-balanced">
              <Card style={pageStyles.pathwayCard}>
                <span style={pageStyles.iconTile}><UserRound size={21} /></span>
                <div><span className="eyebrow">For clients</span><h2>Find a service or post a request</h2><p>Respond to a worker's offer, or describe the outcome you need and compare proposals.</p></div>
                <LinkButton to="/marketplace?kind=OFFER">Browse services <ArrowRight size={15} /></LinkButton>
              </Card>
              <Card style={pageStyles.pathwayCard}>
                <span style={pageStyles.iconTile}><BriefcaseBusiness size={21} /></span>
                <div><span className="eyebrow">For workers</span><h2>Turn your skills into local work</h2><p>Create a service offer or propose on an open client request that fits your experience.</p></div>
                <LinkButton to="/marketplace?kind=REQUEST" variant="secondary">Browse requests <ArrowRight size={15} /></LinkButton>
              </Card>
            </section>

            <TicketSection
              eyebrow="Services"
              title="Popular offers from local workers"
              description="Ready-to-book services with clear pricing and availability."
              tickets={home.data.offers}
              href="/marketplace?kind=OFFER"
            />
            <TicketSection
              eyebrow="Open tasks"
              title="Requests looking for the right worker"
              description="Share your approach and proposed amount with clients nearby."
              tickets={home.data.requests}
              href="/marketplace?kind=REQUEST"
            />
            <section>
              <div className="section-heading">
                <div><span className="eyebrow">Trusted profiles</span><h2>Verified workers</h2><p>Explore public experience, recent work, and client reviews.</p></div>
                <Link className="text-link" to="/workers">Discover workers <ArrowRight size={14} /></Link>
              </div>
              <div className="worker-grid">{home.data.workers.map((worker) => <WorkerCard key={worker.id} worker={worker} />)}</div>
            </section>
          </div>
        )}
      </SimplePage>
    </>
  );
}

function TicketSection({ eyebrow, title, description, tickets, href }: { eyebrow: string; title: string; description: string; tickets: TicketSummary[]; href: string }) {
  return (
    <section>
      <div className="section-heading">
        <div><span className="eyebrow">{eyebrow}</span><h2>{title}</h2><p>{description}</p></div>
        <Link className="text-link" to={href}>View all <ArrowRight size={14} /></Link>
      </div>
      {tickets.length ? <div className="ticket-grid">{tickets.map((ticket) => <TicketCard key={ticket.id} ticket={ticket} />)}</div> : <EmptyState title="No listings yet" description="New marketplace listings will appear here." />}
    </section>
  );
}

interface TicketFilters {
  kind: TicketKind;
  q: string;
  categoryId: string;
  city: string;
  locationMode: "" | LocationMode;
  pricingMode: "" | PricingMode;
  minPrice: string;
  maxPrice: string;
  serviceFrom: string;
  serviceTo: string;
  sort: string;
}

function demoTicketPage(filters: TicketFilters, page: number, size = 12) {
  const normalizedQuery = filters.q.trim().toLowerCase();
  const normalizedCity = filters.city.trim().toLowerCase();
  const min = filters.minPrice === "" ? null : Number(filters.minPrice);
  const max = filters.maxPrice === "" ? null : Number(filters.maxPrice);
  const from = filters.serviceFrom ? new Date(filters.serviceFrom).getTime() : null;
  const to = filters.serviceTo ? new Date(filters.serviceTo).getTime() : null;

  const items = demoTickets.filter((ticket) => {
    if (ticket.kind !== filters.kind) return false;
    if (filters.categoryId && ticket.category.id !== Number(filters.categoryId)) return false;
    if (normalizedQuery && !`${ticket.title} ${ticket.category.name}`.toLowerCase().includes(normalizedQuery)) return false;
    if (normalizedCity && !(ticket.city || "").toLowerCase().includes(normalizedCity)) return false;
    if (filters.locationMode && ticket.locationMode !== filters.locationMode) return false;
    if (filters.pricingMode && ticket.pricingMode !== filters.pricingMode) return false;
    const [low, high] = priceBounds(ticket);
    if ((min !== null || max !== null) && low === null) return false;
    if (min !== null && (high ?? 0) < min) return false;
    if (max !== null && (low ?? 0) > max) return false;
    const ticketStart = ticket.serviceWindowStart ? new Date(ticket.serviceWindowStart).getTime() : null;
    const ticketEnd = ticket.serviceWindowEnd ? new Date(ticket.serviceWindowEnd).getTime() : null;
    if (from !== null && ticketEnd !== null && ticketEnd < from) return false;
    if (to !== null && ticketStart !== null && ticketStart > to) return false;
    return true;
  });

  items.sort((left, right) => {
    const [field, direction] = filters.sort.split(",");
    const multiplier = direction === "asc" ? 1 : -1;
    if (field === "title") return left.title.localeCompare(right.title) * multiplier;
    if (field === "price") return ((priceBounds(left)[0] ?? Number.MAX_SAFE_INTEGER) - (priceBounds(right)[0] ?? Number.MAX_SAFE_INTEGER)) * multiplier;
    return (new Date(left.createdAt).getTime() - new Date(right.createdAt).getTime()) * multiplier;
  });
  return asPage(items, page, size);
}

function filtersFromParams(params: URLSearchParams): TicketFilters {
  const rawKind = params.get("kind");
  return {
    kind: rawKind === "REQUEST" ? "REQUEST" : "OFFER",
    q: params.get("q") || "",
    categoryId: params.get("categoryId") || "",
    city: params.get("city") || "",
    locationMode: (params.get("locationMode") as TicketFilters["locationMode"]) || "",
    pricingMode: (params.get("pricingMode") as TicketFilters["pricingMode"]) || "",
    minPrice: params.get("minPrice") || "",
    maxPrice: params.get("maxPrice") || "",
    serviceFrom: params.get("serviceFrom")?.slice(0, 16) || "",
    serviceTo: params.get("serviceTo")?.slice(0, 16) || "",
    sort: params.get("sort") || "createdAt,desc",
  };
}

function filtersToParams(filters: TicketFilters, page = 0) {
  const params = new URLSearchParams({ kind: filters.kind });
  (Object.keys(filters) as Array<keyof TicketFilters>).forEach((key) => {
    if (key === "kind") return;
    const value = filters[key];
    if (value && !(key === "sort" && value === "createdAt,desc")) params.set(key, value);
  });
  if (page > 0) params.set("page", String(page));
  return params;
}

export function MarketplacePage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const initial = filtersFromParams(searchParams);
  const [filters, setFilters] = useState<TicketFilters>(initial);
  const [filterOpen, setFilterOpen] = useState(false);
  const [page, setPage] = useState(() => Math.max(0, Number(searchParams.get("page") || 0)));
  const categories = useAsyncData(() => api.categories.list(), demoCategories, []);
  const fallback = useMemo(() => demoTicketPage(filters, page), [filters, page]);
  const tickets = useAsyncData<PageResponse<TicketSummary>>(
    () => api.tickets.list({
      kind: filters.kind,
      q: filters.q || undefined,
      categoryId: filters.categoryId ? Number(filters.categoryId) : undefined,
      city: filters.city || undefined,
      locationMode: filters.locationMode || undefined,
      pricingMode: filters.pricingMode || undefined,
      minPrice: filters.minPrice || undefined,
      maxPrice: filters.maxPrice || undefined,
      serviceFrom: filters.serviceFrom ? toIso(filters.serviceFrom) : undefined,
      serviceTo: filters.serviceTo ? toIso(filters.serviceTo) : undefined,
      sort: filters.sort,
      page,
      size: 12,
    }),
    fallback,
    [filters.kind, filters.q, filters.categoryId, filters.city, filters.locationMode, filters.pricingMode, filters.minPrice, filters.maxPrice, filters.serviceFrom, filters.serviceTo, filters.sort, page],
  );

  const updateFilter = <K extends keyof TicketFilters>(key: K, value: TicketFilters[K]) => {
    const next = { ...filters, [key]: value };
    setFilters(next);
    setPage(0);
    setSearchParams(filtersToParams(next));
  };

  const changePage = (nextPage: number) => {
    setPage(nextPage);
    setSearchParams(filtersToParams(filters, nextPage));
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  const reset = () => {
    const next: TicketFilters = { ...filtersFromParams(new URLSearchParams()), kind: filters.kind };
    setFilters(next);
    setPage(0);
    setSearchParams(filtersToParams(next));
  };

  const problem = tickets.error || categories.error;
  const demo = tickets.demo || categories.demo;
  const activeAdvancedFilters = [filters.minPrice, filters.maxPrice, filters.serviceFrom, filters.serviceTo].filter(Boolean).length;

  return (
    <SimplePage>
      <PageTitle
        eyebrow="Marketplace"
        title={filters.kind === "OFFER" ? "Services ready when you are" : "Open requests from local clients"}
        description={filters.kind === "OFFER" ? "Compare public service offers, pricing, and worker profiles." : "Find tasks that fit your skills, schedule, and service area."}
        actions={<LinkButton to={filters.kind === "OFFER" ? "/app/client/requests/new" : "/app/worker/offers/new"} variant="secondary">{filters.kind === "OFFER" ? "Post a request" : "Create an offer"}</LinkButton>}
      />
      <DatasetNotice demo={demo} />
      <div className="segmented" style={{ maxWidth: 460, marginBottom: 18 }} aria-label="Listing type">
        <button className={filters.kind === "OFFER" ? "active" : ""} type="button" onClick={() => updateFilter("kind", "OFFER")}>Services offered</button>
        <button className={filters.kind === "REQUEST" ? "active" : ""} type="button" onClick={() => updateFilter("kind", "REQUEST")}>Client requests</button>
      </div>

      <div className="filter-bar">
        <SearchField value={filters.q} onChange={(value) => updateFilter("q", value)} placeholder="Search titles and descriptions" />
        <label className="field"><span className="field-label">Category</span><select value={filters.categoryId} onChange={(event) => updateFilter("categoryId", event.target.value)}><option value="">All categories</option>{categories.data.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}</select></label>
        <label className="field"><span className="field-label">City</span><input value={filters.city} onChange={(event) => updateFilter("city", event.target.value)} placeholder="Any city" /></label>
        <label className="field"><span className="field-label">Location</span><select value={filters.locationMode} onChange={(event) => updateFilter("locationMode", event.target.value as TicketFilters["locationMode"])}><option value="">Any location</option><option value="ON_SITE">On-site</option><option value="REMOTE">Remote</option><option value="HYBRID">Hybrid</option></select></label>
        <label className="field"><span className="field-label">Pricing</span><select value={filters.pricingMode} onChange={(event) => updateFilter("pricingMode", event.target.value as TicketFilters["pricingMode"])}><option value="">Any pricing</option><option value="FIXED">Fixed</option><option value="BUDGET_RANGE">Budget range</option><option value="OPEN_BID">Open bid</option></select></label>
        <Button type="button" variant="secondary" onClick={() => setFilterOpen(true)}><SlidersHorizontal size={16} /> Filters{activeAdvancedFilters ? ` (${activeAdvancedFilters})` : ""}</Button>
      </div>

      <div className="split" style={{ marginBottom: 18 }}>
        <span className="muted small">{tickets.loading ? "Finding listings…" : `${tickets.data.totalElements} ${tickets.data.totalElements === 1 ? "listing" : "listings"}`}</span>
        <div className="cluster">
          <label className="field"><span className="field-label">Sort</span><select value={filters.sort} onChange={(event) => updateFilter("sort", event.target.value)}><option value="createdAt,desc">Newest</option><option value="price,asc">Price: low to high</option><option value="price,desc">Price: high to low</option><option value="title,asc">Title A–Z</option></select></label>
          <Button type="button" variant="quiet" onClick={reset}>Clear</Button>
        </div>
      </div>

      {tickets.loading ? (
        <LoadingState label="Loading listings" />
      ) : fatalError(problem, demo) ? (
        <ErrorState message={problem?.message || "Unable to load listings."} retry={() => { void categories.reload(); void tickets.reload(); }} />
      ) : tickets.data.items.length ? (
        <>
          <div className="ticket-grid">{tickets.data.items.map((ticket) => <TicketCard key={ticket.id} ticket={ticket} />)}</div>
          <Pagination page={tickets.data.page} totalPages={tickets.data.totalPages} onChange={changePage} />
        </>
      ) : (
        <EmptyState title="No listings match these filters" description="Try a broader location, price range, or category." action={<Button type="button" variant="secondary" onClick={reset}>Clear filters</Button>} />
      )}

      <Modal
        title="More filters"
        open={filterOpen}
        onClose={() => setFilterOpen(false)}
        footer={<><Button type="button" variant="quiet" onClick={reset}>Reset</Button><Button type="button" onClick={() => setFilterOpen(false)}>Show results</Button></>}
      >
        <div className="form-grid">
          <label className="field"><span className="field-label">Minimum price</span><input type="number" min="0" step="0.01" value={filters.minPrice} onChange={(event) => updateFilter("minPrice", event.target.value)} placeholder="$0" /></label>
          <label className="field"><span className="field-label">Maximum price</span><input type="number" min="0" step="0.01" value={filters.maxPrice} onChange={(event) => updateFilter("maxPrice", event.target.value)} placeholder="No maximum" /></label>
          <label className="field"><span className="field-label">Service window starts</span><input type="datetime-local" value={filters.serviceFrom} onChange={(event) => updateFilter("serviceFrom", event.target.value)} /></label>
          <label className="field"><span className="field-label">Service window ends</span><input type="datetime-local" value={filters.serviceTo} onChange={(event) => updateFilter("serviceTo", event.target.value)} /></label>
        </div>
        {filters.minPrice && filters.maxPrice && Number(filters.minPrice) > Number(filters.maxPrice) && <Alert tone="warning" title="Check your price range">Minimum price should not be greater than maximum price.</Alert>}
      </Modal>
    </SimplePage>
  );
}

interface CategoryData {
  category: Category | null;
  tickets: PageResponse<TicketSummary>;
}

function categoryFallback(code: string, kind: TicketKind, page: number): CategoryData {
  const category = demoCategories.find((item) => item.code.toLowerCase() === code.toLowerCase()) || null;
  const items = category ? demoTickets.filter((ticket) => ticket.category.id === category.id && ticket.kind === kind) : [];
  return { category, tickets: asPage(items, page, 12) };
}

export function CategoryPage() {
  const { code = "" } = useParams<{ code: string }>();
  const [searchParams, setSearchParams] = useSearchParams();
  const [kind, setKind] = useState<TicketKind>(searchParams.get("kind") === "REQUEST" ? "REQUEST" : "OFFER");
  const [page, setPage] = useState(() => Math.max(0, Number(searchParams.get("page") || 0)));
  const fallback = useMemo(() => categoryFallback(code, kind, page), [code, kind, page]);
  const resource = useAsyncData<CategoryData>(
    async () => {
      if (!code) throw new ApiError("Category not found", 404, "CATEGORY_NOT_FOUND");
      const category = await api.categories.get(code);
      const tickets = await api.tickets.list({ categoryId: category.id, kind, page, size: 12, sort: "createdAt,desc" });
      return { category, tickets };
    },
    fallback,
    [code, kind, page],
  );

  const selectKind = (next: TicketKind) => {
    setKind(next);
    setPage(0);
    setSearchParams({ kind: next });
  };

  const selectPage = (next: number) => {
    setPage(next);
    setSearchParams(next ? { kind, page: String(next) } : { kind });
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  if (resource.loading) return <SimplePage><LoadingState label="Loading category" /></SimplePage>;
  if (fatalError(resource.error, resource.demo)) {
    if (resource.error?.status === 404) {
      return <SimplePage><EmptyState title="Category not found" description="This category may have moved or is no longer public." action={<LinkButton to="/marketplace">Back to marketplace</LinkButton>} /></SimplePage>;
    }
    return <SimplePage><ErrorState message={resource.error?.message || "Unable to load this category."} retry={() => void resource.reload()} /></SimplePage>;
  }
  if (!resource.data.category) {
    return <SimplePage><DatasetNotice demo={resource.demo} /><EmptyState title="Not available in the offline demo" description="Connect the backend to check this category, or browse the demo marketplace." action={<LinkButton to="/marketplace">Browse marketplace</LinkButton>} /></SimplePage>;
  }

  const category = resource.data.category;
  return (
    <SimplePage>
      <DatasetNotice demo={resource.demo} />
      <PageTitle
        eyebrow="Category"
        title={category.name}
        description={category.description || `Browse ${category.name.toLowerCase()} offers and requests.`}
        actions={<span style={pageStyles.iconTile}><CategoryIcon category={category} /></span>}
      />
      {category.requiredCredential && (
        <Alert tone="info" title="Credential-aware category">
          Workers responding to requests in this category may need a verified {humanCredential(category.requiredCredential).toLowerCase()}.
        </Alert>
      )}
      <div className="segmented" style={{ maxWidth: 440, margin: "22px 0" }}>
        <button type="button" className={kind === "OFFER" ? "active" : ""} onClick={() => selectKind("OFFER")}>Service offers</button>
        <button type="button" className={kind === "REQUEST" ? "active" : ""} onClick={() => selectKind("REQUEST")}>Client requests</button>
      </div>
      {resource.data.tickets.items.length ? (
        <>
          <div className="ticket-grid">{resource.data.tickets.items.map((ticket) => <TicketCard key={ticket.id} ticket={ticket} />)}</div>
          <Pagination page={resource.data.tickets.page} totalPages={resource.data.tickets.totalPages} onChange={selectPage} />
        </>
      ) : (
        <EmptyState title={`No ${kind === "OFFER" ? "service offers" : "client requests"} yet`} description={`There are no open ${category.name.toLowerCase()} listings of this type right now.`} action={<LinkButton to={`/marketplace?kind=${kind}&categoryId=${category.id}`} variant="secondary">Search all filters</LinkButton>} />
      )}
    </SimplePage>
  );
}

interface TicketDetailData {
  ticket: TicketDetail | null;
  category: Category | null;
}

function ticketDetailFallback(ticketId: number): TicketDetailData {
  const ticket = demoTicketDetails.find((item) => item.id === ticketId) || null;
  const category = ticket ? demoCategories.find((item) => item.id === ticket.category.id) || null : null;
  return { ticket, category };
}

interface ResponseFields {
  amount: string;
  message: string;
  start: string;
  end: string;
}

type ResponseErrors = Partial<Record<keyof ResponseFields | "form", string>>;

function validateResponse(ticket: TicketDetail, fields: ResponseFields): ResponseErrors {
  const errors: ResponseErrors = {};
  const amount = Number(fields.amount);
  if (!fields.amount || Number.isNaN(amount) || amount < 0) {
    errors.amount = "Enter a non-negative proposed amount.";
  } else if (ticket.pricingMode === "FIXED" && amount !== Number(ticket.price)) {
    errors.amount = `This fixed listing requires ${money(ticket.price, ticket.currency)}.`;
  } else if (ticket.pricingMode === "BUDGET_RANGE" && (amount < Number(ticket.budgetMin) || amount > Number(ticket.budgetMax))) {
    errors.amount = `Enter an amount from ${money(ticket.budgetMin, ticket.currency)} to ${money(ticket.budgetMax, ticket.currency)}.`;
  }
  if (fields.message.length > 5000) errors.message = "Message must be 5,000 characters or fewer.";

  const scheduleRequired = Boolean(ticket.serviceWindowStart || ticket.serviceWindowEnd);
  if (scheduleRequired && !fields.start) errors.start = "A proposed start is required.";
  if (scheduleRequired && !fields.end) errors.end = "A proposed end is required.";
  if (fields.start && fields.end) {
    const start = new Date(fields.start);
    const end = new Date(fields.end);
    if (start.getTime() <= Date.now()) errors.start = "Start time must be in the future.";
    if (end.getTime() <= start.getTime()) errors.end = "End time must be after the start time.";
    if (ticket.serviceWindowStart && start.getTime() < new Date(ticket.serviceWindowStart).getTime()) errors.start = `Choose a time on or after ${dateTime(ticket.serviceWindowStart)}.`;
    if (ticket.serviceWindowEnd && end.getTime() > new Date(ticket.serviceWindowEnd).getTime()) errors.end = `Choose a time on or before ${dateTime(ticket.serviceWindowEnd)}.`;
    if (ticket.estimatedDurationMinutes && end.getTime() - start.getTime() < ticket.estimatedDurationMinutes * 60_000) errors.end = `Allow at least ${ticket.estimatedDurationMinutes} minutes for this work.`;
  }
  return errors;
}

function responseApiErrors(problem: ApiError): ResponseErrors {
  const amountCodes = new Set(["PROPOSED_AMOUNT_MISMATCH", "PROPOSED_AMOUNT_OUT_OF_RANGE", "INVALID_PROPOSED_AMOUNT"]);
  const scheduleCodes = new Set(["APPLICATION_SCHEDULE_REQUIRED", "APPLICATION_SCHEDULE_IN_PAST", "APPLICATION_SCHEDULE_OUTSIDE_WINDOW", "APPLICATION_DURATION_TOO_SHORT", "INVALID_APPLICATION_SCHEDULE"]);
  if (amountCodes.has(problem.code) || problem.field === "proposedAmount") return { amount: problem.message };
  if (scheduleCodes.has(problem.code) || problem.field === "proposedStart" || problem.field === "proposedEnd") return { start: problem.message, end: problem.message };
  return { form: problem.message };
}

function DetailState({ title, description }: { title: string; description: string }) {
  return <SimplePage><EmptyState title={title} description={description} action={<LinkButton to="/marketplace">Back to marketplace</LinkButton>} /></SimplePage>;
}

export function TicketDetailPage() {
  const { ticketId = "" } = useParams<{ ticketId: string }>();
  const numericId = Number(ticketId);
  const navigate = useNavigate();
  const location = useLocation();
  const { user } = useAuth();
  const fallback = useMemo(() => ticketDetailFallback(numericId), [numericId]);
  const detail = useAsyncData<TicketDetailData>(
    async () => {
      if (!Number.isInteger(numericId) || numericId <= 0) throw new ApiError("Listing not found", 404, "TICKET_NOT_FOUND");
      const ticket = await api.tickets.get(numericId);
      let category: Category | null = null;
      try {
        category = await api.categories.get(ticket.category.code);
      } catch (caught) {
        if (caught instanceof ApiError && caught.status === 404) category = null;
        else throw caught;
      }
      return { ticket, category };
    },
    fallback,
    [numericId],
  );
  const [responseOpen, setResponseOpen] = useState(false);
  const [responseFields, setResponseFields] = useState<ResponseFields>({ amount: "", message: "", start: "", end: "" });
  const [responseErrors, setResponseErrors] = useState<ResponseErrors>({});
  const [submitting, setSubmitting] = useState(false);
  const [submitted, setSubmitted] = useState<Application | true | null>(null);

  if (detail.loading) return <SimplePage><LoadingState label="Loading listing" /></SimplePage>;
  if (fatalError(detail.error, detail.demo)) {
    if (detail.error?.status === 404) return <DetailState title="Listing not found" description="This listing may have moved or is no longer public." />;
    if (detail.error?.status === 409 && (detail.error.code === "TICKET_EXPIRED" || detail.error.message.toLowerCase().includes("expired"))) return <DetailState title="This listing has expired" description="Expired listings no longer accept responses. Browse the marketplace for current work." />;
    return <SimplePage><ErrorState message={detail.error?.message || "Unable to load this listing."} retry={() => void detail.reload()} /></SimplePage>;
  }
  if (!detail.data.ticket) {
    return detail.demo
      ? <SimplePage><DatasetNotice demo /><EmptyState title="Not available in the offline demo" description="Connect the backend to check this listing." action={<LinkButton to="/marketplace">Browse demo listings</LinkButton>} /></SimplePage>
      : <DetailState title="Listing not found" description="This listing may have moved or is no longer public." />;
  }

  const ticket = detail.data.ticket;
  const category = detail.data.category;
  const isAuthor = user?.id === ticket.author.id;
  const mainImage = ticket.images[0]?.image.large || ticket.coverImage?.large;
  const responseLabel = ticket.kind === "REQUEST" ? "Submit a proposal" : "Request this service";
  const managementPath = ticket.kind === "REQUEST" ? `/app/client/requests/${ticket.id}` : `/app/worker/offers/${ticket.id}`;

  const openResponse = () => {
    setResponseFields({
      amount: responseAmount(ticket),
      message: "",
      start: toLocalDateTime(ticket.serviceWindowStart),
      end: toLocalDateTime(ticket.serviceWindowEnd),
    });
    setResponseErrors({});
    setResponseOpen(true);
  };

  const submitResponse = async (event?: FormEvent) => {
    event?.preventDefault();
    if (detail.demo) {
      setResponseErrors({ form: "Connect the backend before submitting a real marketplace response." });
      return;
    }
    const errors = validateResponse(ticket, responseFields);
    if (Object.keys(errors).length) {
      setResponseErrors(errors);
      return;
    }
    setSubmitting(true);
    setResponseErrors({});
    try {
      const application = await api.applications.apply(ticket.id, {
        proposedAmount: responseFields.amount,
        message: responseFields.message.trim() || undefined,
        proposedStart: toIso(responseFields.start),
        proposedEnd: toIso(responseFields.end),
      });
      setSubmitted(application);
      detail.setData((current) => current.ticket ? { ...current, ticket: { ...current.ticket, applicationCount: current.ticket.applicationCount + 1 } } : current);
      setResponseOpen(false);
    } catch (caught) {
      const problem = caught instanceof ApiError ? caught : new ApiError("Unable to submit your response");
      if (problem.status === 401) {
        navigate("/login", { state: { from: location.pathname } });
        return;
      }
      if (problem.code === "TICKET_NOT_OPEN" || problem.code === "TICKET_EXPIRED") {
        setResponseOpen(false);
        void detail.reload();
        return;
      }
      setResponseErrors(responseApiErrors(problem));
    } finally {
      setSubmitting(false);
    }
  };

  let action: ReactNode;
  if (!user) {
    action = <Link className="button button-primary button-block" to="/login" state={{ from: location.pathname }}>Sign in to respond</Link>;
  } else if (isAuthor) {
    action = <LinkButton to={managementPath}>Manage this listing <ArrowRight size={15} /></LinkButton>;
  } else if (submitted) {
    action = <Alert tone="success" title="Response submitted">The listing author can now review your response. This page cannot recover its status after a refresh yet.</Alert>;
  } else if (ticket.status !== "OPEN") {
    action = <Alert tone="info" title={ticket.status === "MATCHED" ? "This listing is matched" : "This listing is closed"}>It remains visible for context but no longer accepts responses.</Alert>;
  } else {
    action = <Button type="button" className="button-block" onClick={openResponse}>{responseLabel} <ArrowRight size={15} /></Button>;
  }

  return (
    <SimplePage>
      <DatasetNotice demo={detail.demo} />
      <div className="detail-hero">
        <div>
          <div className="gallery-main">{mainImage ? <img src={assetUrl(mainImage)} alt="" /> : <span className="image-placeholder" />}</div>
          {ticket.images.length > 1 && <div className="photo-grid" aria-label="Listing photos">{ticket.images.slice(1, 5).map((image) => <img key={image.id} src={assetUrl(image.image.thumb)} alt={image.caption || ""} />)}</div>}
          <div className="detail-heading">
            <div className="cluster"><Badge value={ticket.kind} /><Badge value={ticket.status} /><span className="muted small">{ticket.category.name}</span></div>
            <h1>{ticket.title}</h1>
            <div className="detail-meta">
              <span><MapPin size={14} /> {locationLabels[ticket.locationMode]}{ticket.city ? ` · ${ticket.city}` : ""}</span>
              <span><CalendarDays size={14} /> Posted {date(ticket.createdAt)}</span>
              <span><UsersRound size={14} /> {ticket.applicationCount} {ticket.applicationCount === 1 ? "response" : "responses"}</span>
            </div>
          </div>
          <Card className="prose-card">
            <h2>About this {ticket.kind === "OFFER" ? "service" : "request"}</h2>
            <p>{ticket.description || "The listing author has not added a description yet."}</p>
          </Card>
          <Card className="prose-card">
            <h2>{ticket.kind === "OFFER" ? "Service provider" : "Posted by"}</h2>
            <div className="cluster">
              <Avatar name={ticket.worker?.displayName || ticket.author.name} src={ticket.worker?.avatarUrl || ticket.author.avatarUrl} size="lg" />
              <div>
                {ticket.worker ? <Link className="ticket-title" to={`/workers/${ticket.worker.id}`}>{ticket.worker.displayName}</Link> : <strong>{ticket.author.name}</strong>}
                <div className="cluster" style={{ marginTop: 5 }}>
                  <Rating value={ticket.worker?.rating || ticket.author.rating} count={ticket.worker?.reviewCount} />
                  {ticket.worker?.verified && <span className="verified"><ShieldCheck size={13} /> Verified profile</span>}
                </div>
              </div>
            </div>
          </Card>
        </div>

        <aside className="detail-sidebar">
          <Card className="panel">
            <span className="eyebrow">{pricingLabels[ticket.pricingMode]}</span>
            <div className="detail-price">{ticketPrice(ticket)}</div>
            <div className="summary-list">
              <div className="summary-item"><MapPin size={17} /><div><span>Location</span><strong>{locationLabels[ticket.locationMode]}{ticket.address ? ` · ${ticket.address}` : ticket.city ? ` · ${ticket.city}` : ""}</strong></div></div>
              <div className="summary-item"><CalendarDays size={17} /><div><span>Service window</span><strong>{ticket.serviceWindowStart ? `${dateTime(ticket.serviceWindowStart)} – ${dateTime(ticket.serviceWindowEnd)}` : "Flexible"}</strong></div></div>
              <div className="summary-item"><Clock3 size={17} /><div><span>Estimated duration</span><strong>{ticket.estimatedDurationMinutes ? `${ticket.estimatedDurationMinutes} minutes` : "Not specified"}</strong></div></div>
              <div className="summary-item"><ShieldCheck size={17} /><div><span>Response flow</span><strong>Author reviews before an engagement begins</strong></div></div>
            </div>
            {action}
          </Card>
          {detail.demo && <Alert tone="warning" title="Read-only demo">The API is offline, so responses cannot be submitted from this listing.</Alert>}
          <Alert tone="info" title="Clear next step">If the author accepts a response, the client funds the engagement before work starts. Funding status is shown in the workspace.</Alert>
        </aside>
      </div>

      <Modal
        title={responseLabel}
        open={responseOpen}
        onClose={() => !submitting && setResponseOpen(false)}
        footer={<><Button type="button" variant="quiet" disabled={submitting} onClick={() => setResponseOpen(false)}>Cancel</Button><Button type="button" busy={submitting} disabled={detail.demo} onClick={() => void submitResponse()}>{ticket.kind === "REQUEST" ? "Submit proposal" : "Send request"}</Button></>}
      >
        <form className="stack" onSubmit={submitResponse}>
          {responseErrors.form && <Alert tone="danger" title="Could not submit">{responseErrors.form}</Alert>}
          {detail.demo && <Alert tone="warning" title="Backend connection required">You can review this form in demo mode, but submitting is disabled until the API is available.</Alert>}
          {ticket.kind === "REQUEST" && category?.requiredCredential && (
            <Alert tone="info" title="Credential required" action={<LinkButton to="/app/worker/credentials" variant="quiet">Manage</LinkButton>}>
              This category requires a verified {humanCredential(category.requiredCredential).toLowerCase()} before an eligible worker can propose. Manage credentials in your worker workspace.
            </Alert>
          )}
          <label className={`field ${responseErrors.amount ? "field-error" : ""}`}>
            <span className="field-label">Proposed amount ({ticket.currency})</span>
            <input type="number" min="0" step="0.01" readOnly={ticket.pricingMode === "FIXED"} value={responseFields.amount} onChange={(event) => setResponseFields((current) => ({ ...current, amount: event.target.value }))} />
            <small>{responseErrors.amount || (ticket.pricingMode === "FIXED" ? "Fixed by the listing." : ticket.pricingMode === "BUDGET_RANGE" ? `Allowed range: ${money(ticket.budgetMin, ticket.currency)}–${money(ticket.budgetMax, ticket.currency)}` : "Choose a clear, non-negative amount.")}</small>
          </label>
          <label className={`field ${responseErrors.message ? "field-error" : ""}`}>
            <span className="field-label">Message</span>
            <textarea maxLength={5000} value={responseFields.message} onChange={(event) => setResponseFields((current) => ({ ...current, message: event.target.value }))} placeholder={ticket.kind === "REQUEST" ? "Share your approach and relevant experience." : "Describe what you need and any useful context."} />
            <small>{responseErrors.message || `${responseFields.message.length}/5,000 characters`}</small>
          </label>
          <div className="form-grid">
            <label className={`field ${responseErrors.start ? "field-error" : ""}`}><span className="field-label">Proposed start</span><input type="datetime-local" value={responseFields.start} onChange={(event) => setResponseFields((current) => ({ ...current, start: event.target.value }))} /><small>{responseErrors.start}</small></label>
            <label className={`field ${responseErrors.end ? "field-error" : ""}`}><span className="field-label">Proposed end</span><input type="datetime-local" value={responseFields.end} onChange={(event) => setResponseFields((current) => ({ ...current, end: event.target.value }))} /><small>{responseErrors.end}</small></label>
          </div>
          <Alert tone="info">Acceptance creates an engagement. The client then funds it; payment and funding states remain visible to both parties.</Alert>
        </form>
      </Modal>
    </SimplePage>
  );
}

export function WorkersPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [verifiedOnly, setVerifiedOnly] = useState(searchParams.get("verified") === "true");
  const [page, setPage] = useState(() => Math.max(0, Number(searchParams.get("page") || 0)));
  const fallback = useMemo(
    () => asPage(verifiedOnly ? demoWorkers.filter((worker) => worker.verified) : demoWorkers, page, 12),
    [verifiedOnly, page],
  );
  const workers = useAsyncData<PageResponse<WorkerProfile>>(
    () => api.workers.list(page, 12, verifiedOnly),
    fallback,
    [page, verifiedOnly],
  );

  const selectVerified = (value: boolean) => {
    setVerifiedOnly(value);
    setPage(0);
    setSearchParams(value ? { verified: "true" } : {});
  };

  const selectPage = (next: number) => {
    setPage(next);
    const params = new URLSearchParams();
    if (verifiedOnly) params.set("verified", "true");
    if (next) params.set("page", String(next));
    setSearchParams(params);
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  return (
    <SimplePage>
      <PageTitle
        eyebrow="Worker directory"
        title="Meet local service professionals"
        description="Explore public experience, completed work, ratings, and client reviews before choosing a listing."
        actions={<LinkButton to="/app/worker/profile" variant="secondary">Build your worker profile</LinkButton>}
      />
      <DatasetNotice demo={workers.demo} />
      <div className="segmented" style={{ maxWidth: 390, marginBottom: 24 }}>
        <button type="button" className={!verifiedOnly ? "active" : ""} onClick={() => selectVerified(false)}>All workers</button>
        <button type="button" className={verifiedOnly ? "active" : ""} onClick={() => selectVerified(true)}>Verified profiles</button>
      </div>
      {workers.loading ? (
        <LoadingState label="Loading workers" />
      ) : fatalError(workers.error, workers.demo) ? (
        <ErrorState message={workers.error?.message || "Unable to load workers."} retry={() => void workers.reload()} />
      ) : workers.data.items.length ? (
        <>
          <div className="worker-grid">{workers.data.items.map((worker) => <WorkerCard key={worker.id} worker={worker} />)}</div>
          <Pagination page={workers.data.page} totalPages={workers.data.totalPages} onChange={selectPage} />
        </>
      ) : (
        <EmptyState title="No worker profiles found" description={verifiedOnly ? "Try viewing all public worker profiles." : "Public worker profiles will appear here."} action={verifiedOnly ? <Button type="button" variant="secondary" onClick={() => selectVerified(false)}>View all workers</Button> : undefined} />
      )}
    </SimplePage>
  );
}

interface WorkerProfileData {
  worker: WorkerProfile | null;
  reviews: PageResponse<Review>;
}

function workerProfileFallback(workerId: number, page: number): WorkerProfileData {
  const worker = demoWorkers.find((item) => item.id === workerId) || null;
  const reviews = worker
    ? demoReviews.filter((review) => review.direction === "CLIENT_TO_WORKER" && review.reviewee.id === worker.userId)
    : [];
  return { worker, reviews: asPage(reviews, page, 8) };
}

export function WorkerProfilePage() {
  const { workerId = "" } = useParams<{ workerId: string }>();
  const numericId = Number(workerId);
  const [searchParams, setSearchParams] = useSearchParams();
  const [page, setPage] = useState(() => Math.max(0, Number(searchParams.get("page") || 0)));
  const fallback = useMemo(() => workerProfileFallback(numericId, page), [numericId, page]);
  const profile = useAsyncData<WorkerProfileData>(
    async () => {
      if (!Number.isInteger(numericId) || numericId <= 0) throw new ApiError("Worker profile not found", 404, "WORKER_NOT_FOUND");
      const [worker, reviews] = await Promise.all([api.workers.get(numericId), api.workers.reviews(numericId, page, 8)]);
      return { worker, reviews };
    },
    fallback,
    [numericId, page],
  );

  const selectPage = (next: number) => {
    setPage(next);
    setSearchParams(next ? { page: String(next) } : {});
    document.getElementById("reviews")?.scrollIntoView({ behavior: "smooth" });
  };

  if (profile.loading) return <SimplePage><LoadingState label="Loading worker profile" /></SimplePage>;
  if (fatalError(profile.error, profile.demo)) {
    if (profile.error?.status === 404) return <SimplePage><EmptyState title="Worker profile not found" description="This profile may be private or no longer available." action={<LinkButton to="/workers">Back to workers</LinkButton>} /></SimplePage>;
    return <SimplePage><ErrorState message={profile.error?.message || "Unable to load this worker profile."} retry={() => void profile.reload()} /></SimplePage>;
  }
  if (!profile.data.worker) {
    return <SimplePage><DatasetNotice demo={profile.demo} /><EmptyState title="Not available in the offline demo" description="Connect the backend to check this worker, or choose a demo profile." action={<LinkButton to="/workers">Browse workers</LinkButton>} /></SimplePage>;
  }

  const worker = profile.data.worker;
  return (
    <SimplePage>
      <DatasetNotice demo={profile.demo} />
      <Card style={pageStyles.profileHero}>
        <Avatar name={worker.displayName} size="xl" />
        <div style={{ flex: "1 1 300px" }}>
          <div className="cluster">
            <span className="eyebrow" style={{ margin: 0 }}>Worker profile</span>
            {worker.verified && <span className="verified"><ShieldCheck size={14} /> Verified profile</span>}
          </div>
          <h1 style={{ fontFamily: "inherit", fontSize: "clamp(1.8rem, 4vw, 2.8rem)", margin: "8px 0" }}>{worker.displayName}</h1>
          <p style={{ marginBottom: 8 }}>{worker.headline || "Local service professional"}</p>
          <div className="cluster"><Rating value={worker.rating} count={worker.reviewCount} /><span className="muted small">{worker.completedJobs} completed jobs</span>{worker.address && <span className="muted small"><MapPin size={13} /> {worker.address}</span>}</div>
        </div>
        <LinkButton to="/marketplace?kind=OFFER">Browse service offers <ArrowRight size={15} /></LinkButton>
      </Card>

      <nav className="tabs" style={{ marginTop: 24 }} aria-label="Profile sections">
        <a className="active" href="#overview">Overview</a>
        <a href="#recent-work">Recent work</a>
        <a href="#reviews">Client reviews</a>
      </nav>

      <div className="content-grid" id="overview">
        <div className="stack" style={{ gap: 22 }}>
          <Card className="panel">
            <h2>About</h2>
            <p>{worker.description || "This worker has not added a public description yet."}</p>
            <div className="summary-list">
              <div className="summary-item"><BadgeCheck size={17} /><div><span>Public profile status</span><strong>{worker.verified ? "Verified profile" : "Not marked verified"}</strong></div></div>
              <div className="summary-item"><MapPin size={17} /><div><span>Service radius</span><strong>{worker.serviceRadiusKm ? `${worker.serviceRadiusKm} km` : "Not specified"}</strong></div></div>
              <div className="summary-item"><CalendarDays size={17} /><div><span>Member since</span><strong>{date(worker.createdAt)}</strong></div></div>
            </div>
          </Card>

          <Card className="panel" id="recent-work">
            <h2>Recent work</h2>
            {worker.recentWork.length ? (
              <div style={pageStyles.recentGrid}>
                {worker.recentWork.map((work) => (
                  <Link key={work.ticketId} to={`/tickets/${work.ticketId}`}>
                    <img style={pageStyles.recentImage} src={assetUrl(work.image.thumb)} alt="" />
                    <strong className="ticket-title" style={{ display: "block", marginTop: 8 }}>{work.ticketTitle}</strong>
                  </Link>
                ))}
              </div>
            ) : <EmptyState title="No recent work shared" description="Completed work can appear here when the worker makes it public." />}
          </Card>

          <Card className="panel" id="reviews">
            <div className="split"><h2>Client reviews</h2><span className="muted small">{profile.data.reviews.totalElements} total</span></div>
            {profile.data.reviews.items.length ? (
              <div>
                {profile.data.reviews.items.map((review) => (
                  <article className="list-row" key={review.id} style={{ alignItems: "flex-start" }}>
                    <Avatar name={review.reviewer.name} src={review.reviewer.avatarUrl} size="md" />
                    <div className="list-row-main" style={{ flex: 1 }}>
                      <strong>{review.reviewer.name}</strong>
                      <Rating value={review.rating} compact />
                      <p className="small" style={{ margin: "8px 0 0" }}>{review.comment || "The client left a rating without a written comment."}</p>
                    </div>
                    <time className="muted small">{date(review.createdAt)}</time>
                  </article>
                ))}
                <Pagination page={profile.data.reviews.page} totalPages={profile.data.reviews.totalPages} onChange={selectPage} />
              </div>
            ) : <EmptyState title="No client reviews yet" description="Client-to-worker reviews from completed engagements will appear here." />}
          </Card>
        </div>

        <aside className="detail-sidebar">
          <Card className="panel">
            <h2>Profile at a glance</h2>
            <div className="summary-list">
              <div className="summary-item"><Star size={17} /><div><span>Rating</span><strong>{Number(worker.rating).toFixed(1)} from {worker.reviewCount} reviews</strong></div></div>
              <div className="summary-item"><CheckCircle2 size={17} /><div><span>Completed work</span><strong>{worker.completedJobs} jobs</strong></div></div>
              <div className="summary-item"><ShieldCheck size={17} /><div><span>Verification</span><strong>{worker.verified ? "Profile verified" : "Not verified"}</strong></div></div>
            </div>
            <LinkButton to="/marketplace?kind=OFFER">Browse marketplace</LinkButton>
          </Card>
          <Alert tone="info" title="Choose through a listing">This public profile does not support direct hire. Open a service offer in the marketplace to respond through the tracked workflow.</Alert>
        </aside>
      </div>
    </SimplePage>
  );
}

function Journey({ title, intro, steps }: { title: string; intro: string; steps: string[] }) {
  return (
    <Card style={pageStyles.pathwayCard}>
      <div><h3>{title}</h3><p className="small" style={{ margin: 0 }}>{intro}</p></div>
      <div style={pageStyles.lifecycle}>
        {steps.map((step, index) => <div key={step} style={pageStyles.lifecycleStep}><span style={pageStyles.lifecycleNumber}>{index + 1}</span><strong className="small">{step}</strong></div>)}
      </div>
    </Card>
  );
}

export function HowItWorksPage() {
  const [audience, setAudience] = useState<"client" | "worker">("client");
  const clientShared = ["Fund the engagement after a response is accepted", "Track clear engagement status", "Approve delivery or open a dispute", "Review the worker once"];
  const workerShared = ["Wait for the client to fund the engagement", "Start work after funding is visible", "Add private evidence and mark work delivered", "Wait for approval or dispute", "Review the client once"];

  return (
    <>
      <section style={pageStyles.hero}>
        <div className="container" style={{ maxWidth: 880, textAlign: "center" }}>
          <span className="eyebrow">How it works</span>
          <h1>One marketplace, two clear paths.</h1>
          <p style={{ fontSize: "1rem", margin: "0 auto 24px", maxWidth: 720 }}>Whether you need help or provide it, each response moves through an explicit acceptance, funding, delivery, and review lifecycle.</p>
          <div className="cluster" style={{ justifyContent: "center" }}><LinkButton to="/marketplace?kind=OFFER">Find a service</LinkButton><LinkButton to="/marketplace?kind=REQUEST" variant="secondary">Find a request</LinkButton></div>
        </div>
      </section>
      <SimplePage>
        <div className="segmented" style={{ maxWidth: 420, margin: "0 auto 28px" }}>
          <button type="button" className={audience === "client" ? "active" : ""} onClick={() => setAudience("client")}>I need help</button>
          <button type="button" className={audience === "worker" ? "active" : ""} onClick={() => setAudience("worker")}>I provide services</button>
        </div>

        {audience === "client" ? (
          <section>
            <PageTitle eyebrow="Client journey" title="Choose the starting point that fits" description="Both routes join the same tracked engagement after one response is accepted." />
            <div className="content-grid content-grid-balanced">
              <Journey title="Post a request" intro="Describe the outcome you need and let workers propose." steps={["Post a REQUEST", "A worker proposes", "You accept one proposal", ...clientShared]} />
              <Journey title="Respond to an offer" intro="Start from a service a worker has already published." steps={["Respond to an OFFER", "The worker accepts", ...clientShared]} />
            </div>
          </section>
        ) : (
          <section>
            <PageTitle eyebrow="Worker journey" title="Publish your service or find open work" description="Acceptance must happen before the client can fund and work can begin." />
            <div className="content-grid content-grid-balanced">
              <Journey title="Create an offer" intro="Package your service so clients can respond." steps={["Create an OFFER", "Clients respond", "You accept one response", ...workerShared]} />
              <Journey title="Propose on a request" intro="Find a client task that matches your service area." steps={["Browse REQUESTS", "You propose", "The client accepts", ...workerShared]} />
            </div>
          </section>
        )}

        <section style={{ marginTop: 58 }}>
          <div className="section-heading"><div><span className="eyebrow">Designed for clarity</span><h2>What the platform tracks</h2></div></div>
          <div className="worker-grid">
            <Card style={pageStyles.pathwayCard}><CircleDollarSign size={24} color="#127a4a" /><h3>Funding and outcomes</h3><p className="small">The workspace records funding, approval, refund, and entitlement states. Provider behavior depends on the configured payment system.</p></Card>
            <Card style={pageStyles.pathwayCard}><ShieldCheck size={24} color="#127a4a" /><h3>Profile and credentials</h3><p className="small">A verified profile flag and category-specific credential review are separate signals, each shown in the appropriate context.</p></Card>
            <Card style={pageStyles.pathwayCard}><ClipboardCheck size={24} color="#127a4a" /><h3>Explicit status</h3><p className="small">Applications and engagements expose their current state so each person knows which action is available next.</p></Card>
          </div>
        </section>

        <Alert tone="info" title="Current product boundary">
          This marketplace does not include chat, availability calendars, direct-hire buttons, or off-platform payment coordination. Messages attached to responses and private delivery evidence stay inside the tracked workflow.
        </Alert>
      </SimplePage>
    </>
  );
}

interface LegalSection {
  id: string;
  title: string;
  paragraphs: string[];
}

const termsSections: LegalSection[] = [
  {
    id: "marketplace-role",
    title: "Marketplace role",
    paragraphs: [
      "This section will explain the platform's role in connecting clients and workers, including which party provides each listed service.",
      "Approved language must define the legal relationship between users and the platform before publication.",
    ],
  },
  {
    id: "accounts",
    title: "Accounts and eligibility",
    paragraphs: [
      "Final terms will describe account eligibility, accurate profile information, credential obligations, and acceptable use.",
      "Registration currently captures an interface acknowledgement only; consent versioning and audit records require a production implementation.",
    ],
  },
  {
    id: "engagements",
    title: "Listings and engagements",
    paragraphs: [
      "Final copy will cover offers, requests, responses, acceptance, funding, delivery evidence, approval, cancellation, and disputes.",
      "No legal promise about escrow, guarantees, employment, insurance, or service quality is made by this placeholder.",
    ],
  },
  {
    id: "payments",
    title: "Payments and refunds",
    paragraphs: [
      "Approved terms must identify the payment provider, authorization model, fees, payout conditions, refunds, and dispute handling for the configured production environment.",
    ],
  },
  {
    id: "contact",
    title: "Questions and notices",
    paragraphs: [
      "Publication-ready contact details, notice procedures, governing law, and effective dates have not yet been supplied.",
    ],
  },
];

const privacySections: LegalSection[] = [
  {
    id: "collection",
    title: "Information collected",
    paragraphs: [
      "Final copy will identify account, profile, listing, response, engagement, credential, payment-status, file, and technical information processed by the production service.",
      "The approved notice must distinguish required information from optional public profile content.",
    ],
  },
  {
    id: "use",
    title: "How information is used",
    paragraphs: [
      "A reviewed policy must describe marketplace operation, safety, eligibility, payment administration, support, security, legal compliance, and product improvement purposes.",
    ],
  },
  {
    id: "sharing",
    title: "Sharing and visibility",
    paragraphs: [
      "Final language will explain which listing and profile fields are public, which engagement evidence stays private, and which service providers receive data.",
      "This placeholder does not name subprocessors or claim that a particular international transfer mechanism applies.",
    ],
  },
  {
    id: "retention",
    title: "Retention and security",
    paragraphs: [
      "Approved retention periods, deletion rules, backup practices, and security disclosures are still required for production publication.",
    ],
  },
  {
    id: "rights",
    title: "Choices and privacy rights",
    paragraphs: [
      "The final notice must provide jurisdiction-appropriate rights, request methods, identity verification steps, appeal routes, and contact details.",
    ],
  },
];

export function LegalPage({ kind }: { kind?: "terms" | "privacy" } = {}) {
  const location = useLocation();
  const documentKind = kind || (location.pathname.toLowerCase().includes("privacy") ? "privacy" : "terms");
  const isPrivacy = documentKind === "privacy";
  const sections = isPrivacy ? privacySections : termsSections;

  return (
    <SimplePage>
      <PageTitle
        eyebrow="Legal"
        title={isPrivacy ? "Privacy notice" : "Terms of service"}
        description="A structured placeholder for reviewed, publication-ready legal copy."
        actions={<Button type="button" variant="secondary" onClick={() => window.print()}>Print placeholder</Button>}
      />
      <Alert tone="warning" title="Draft content placeholder">
        Legal review and approved copy are required before publication. The sections below describe coverage only and are not operative terms or a privacy notice.
      </Alert>
      <div className="legal-layout" style={{ marginTop: 28 }}>
        <nav className="legal-nav" aria-label={`${isPrivacy ? "Privacy" : "Terms"} sections`}>
          <strong>On this page</strong>
          {sections.map((section) => <a key={section.id} href={`#${section.id}`}>{section.title}</a>)}
          <Link to={isPrivacy ? "/terms" : "/privacy"}>{isPrivacy ? "View Terms" : "View Privacy"} <ArrowRight size={13} /></Link>
        </nav>
        <Card className="legal-content">
          {sections.map((section) => (
            <section id={section.id} key={section.id}>
              <h2>{section.title}</h2>
              {section.paragraphs.map((paragraph) => <p key={paragraph}>{paragraph}</p>)}
            </section>
          ))}
        </Card>
      </div>
    </SimplePage>
  );
}

export function TermsPage() {
  return <LegalPage kind="terms" />;
}

export function PrivacyPage() {
  return <LegalPage kind="privacy" />;
}

export function AccountPage() {
  const { user, loading, demo, logout } = useAuth();
  const navigate = useNavigate();
  const [signingOut, setSigningOut] = useState(false);

  const signOut = async () => {
    setSigningOut(true);
    try {
      await logout();
      navigate("/", { replace: true });
    } finally {
      setSigningOut(false);
    }
  };

  if (loading) return <SimplePage><LoadingState label="Loading account" /></SimplePage>;
  if (!user) return <SimplePage><EmptyState title="Sign in to view your account" description="Your account summary and workspace links are available after sign-in." action={<LinkButton to="/login">Sign in</LinkButton>} /></SimplePage>;

  if (user.status !== "ACTIVE") {
    return (
      <SimplePage>
        <PageTitle eyebrow="Account" title="Account access is limited" description={`This account is currently ${user.status.toLowerCase()}.`} />
        <Alert tone="danger" title={`${titleCase(user.status)} account`}>Workspace actions are unavailable. Contact the marketplace operator for an account review.</Alert>
        <Button type="button" variant="secondary" busy={signingOut} onClick={() => void signOut()}>Sign out</Button>
      </SimplePage>
    );
  }

  const isAdmin = user.role === "ADMIN";
  return (
    <SimplePage>
      <PageTitle
        eyebrow="Account"
        title="Your account"
        description="Review identity, capabilities, reputation, and workspace entry points."
        actions={<Button type="button" variant="secondary" busy={signingOut} onClick={() => void signOut()}>Sign out</Button>}
      />
      <DatasetNotice demo={demo} />
      <div className="content-grid">
        <div className="stack" style={{ gap: 22 }}>
          <Card className="panel">
            <div style={pageStyles.profileHero}>
              <Avatar name={user.name} src={user.avatarUrl} size="xl" />
              <div>
                <div className="cluster"><h2 style={{ margin: 0 }}>{user.name}</h2><Badge value={user.status} /><Badge value={user.role} /></div>
                <p className="small" style={{ margin: "7px 0" }}>{user.email}</p>
                <div className="cluster">{user.capabilities.map((capability) => <Badge key={capability} value={capability} />)}</div>
              </div>
            </div>
          </Card>

          <Card className="panel">
            <h2>Account details</h2>
            <div className="list-row"><div className="list-row-main"><strong>Email</strong><span>Used for sign-in and account communication</span></div><span className="small">{user.email}</span></div>
            <div className="list-row"><div className="list-row-main"><strong>Phone</strong><span>Contact information on your account</span></div><span className="small">{user.phone || "Not provided"}</span></div>
            <div className="list-row"><div className="list-row-main"><strong>Member since</strong><span>Account creation date</span></div><span className="small">{date(user.createdAt)}</span></div>
            {!isAdmin && <div className="list-row"><div className="list-row-main"><strong>Client reputation</strong><span>Reviews received as a client</span></div><Rating value={user.clientRating} count={user.clientReviewCount} /></div>}
          </Card>
        </div>

        <aside className="stack" style={{ gap: 22 }}>
          <Card className="panel">
            <h2>{isAdmin ? "Administration" : "Your workspaces"}</h2>
            {isAdmin ? (
              <Link className="list-row" to="/admin"><div className="cluster"><ShieldCheck size={18} color="#127a4a" /><div className="list-row-main"><strong>Admin workspace</strong><span>Casework, finance, categories, and audit</span></div></div><ChevronRight size={17} /></Link>
            ) : (
              <>
                <Link className="list-row" to="/app/client"><div className="cluster"><UserRound size={18} color="#127a4a" /><div className="list-row-main"><strong>Client workspace</strong><span>Requests, responses, engagements, and reputation</span></div></div><ChevronRight size={17} /></Link>
                <Link className="list-row" to="/app/worker"><div className="cluster"><BriefcaseBusiness size={18} color="#127a4a" /><div className="list-row-main"><strong>Worker workspace</strong><span>Offers, proposals, engagements, and earnings</span></div></div><ChevronRight size={17} /></Link>
                <Link className="list-row" to="/app/worker/profile"><div className="cluster"><UsersRound size={18} color="#127a4a" /><div className="list-row-main"><strong>Worker profile</strong><span>Edit the profile shown in discovery</span></div></div><ChevronRight size={17} /></Link>
                <Link className="list-row" to="/app/worker/credentials"><div className="cluster"><FileCheck2 size={18} color="#127a4a" /><div className="list-row-main"><strong>Credentials</strong><span>Submit and review category eligibility</span></div></div><ChevronRight size={17} /></Link>
                <Link className="list-row" to="/app/client/reputation"><div className="cluster"><Star size={18} color="#127a4a" /><div className="list-row-main"><strong>Client reputation</strong><span>See feedback from completed engagements</span></div></div><ChevronRight size={17} /></Link>
              </>
            )}
          </Card>
          <Alert tone="info" title="Profile changes">
            Account identity fields are read-only here because the current API does not expose an account-edit endpoint. Worker profile details are managed in the worker workspace.
          </Alert>
        </aside>
      </div>
    </SimplePage>
  );
}

export function ForbiddenPage() {
  const navigate = useNavigate();
  return (
    <div className="not-found">
      <strong>403</strong>
      <h1>This area is not available to your account</h1>
      <p>Your current role does not have permission to open this workspace.</p>
      <div className="cluster"><Button type="button" variant="secondary" onClick={() => navigate(-1)}>Go back</Button><LinkButton to="/">Go home</LinkButton></div>
    </div>
  );
}

export function NotFoundPage() {
  return (
    <div className="not-found">
      <strong>404</strong>
      <h1>We could not find that page</h1>
      <p>The address may be outdated, or the page may have moved.</p>
      <div className="cluster"><LinkButton to="/marketplace" variant="secondary">Back to marketplace</LinkButton><LinkButton to="/">Go home</LinkButton></div>
    </div>
  );
}
