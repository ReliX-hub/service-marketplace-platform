import { useEffect } from "react";
import { Navigate, Route, Routes, useLocation } from "react-router-dom";
import { useAuth } from "./auth/AuthContext";
import { ProtectedRoute, PublicLayout, WorkspaceLayout } from "./components/layout";
import {
  AccountPage,
  CategoryPage,
  ForbiddenPage,
  HomePage,
  HowItWorksPage,
  MarketplacePage,
  NotFoundPage,
  PrivacyPage,
  TermsPage,
  TicketDetailPage,
  WorkerProfilePage,
  WorkersPage,
} from "./pages/public/PublicPages";
import { LoginPage, RegisterPage } from "./pages/auth/AuthPages";
import { ClientDashboardPage, WorkerDashboardPage } from "./pages/workspace/DashboardPages";
import { ApplicationsPage, ListingComposerPage, ListingManagementPage, MyListingsPage } from "./pages/workspace/ListingPages";
import {
  EarningsPage,
  EngagementDetailPage,
  EngagementListPage,
  PaymentPage,
  RefundDetailPage,
  RefundListPage,
  ReputationPage,
  SettlementDetailPage,
} from "./pages/workspace/EngagementPages";
import { CredentialNewPage, CredentialsPage, WorkerProfileSettingsPage } from "./pages/workspace/WorkerSettingsPages";
import {
  AdminAuditPage,
  AdminBatchesPage,
  AdminCategoriesPage,
  AdminCredentialsPage,
  AdminDisputeDetailPage,
  AdminDisputesPage,
  AdminFinancePage,
  AdminOverviewPage,
  AdminRefundsPage,
} from "./pages/admin/AdminPages";

function ScrollToTop() {
  const { pathname } = useLocation();
  useEffect(() => {
    window.scrollTo({ top: 0, behavior: "instant" });
  }, [pathname]);
  return null;
}

function AppLanding() {
  const { user } = useAuth();
  if (user?.role === "ADMIN") return <Navigate to="/admin" replace />;
  const last = localStorage.getItem("marketplace.lastWorkspace");
  return <Navigate to={last === "worker" ? "/app/worker" : "/app/client"} replace />;
}

function WorkspacePreference({ mode }: { mode: "client" | "worker" }) {
  useEffect(() => {
    localStorage.setItem("marketplace.lastWorkspace", mode);
  }, [mode]);
  return <WorkspaceLayout mode={mode} />;
}

export default function App() {
  return <>
    <ScrollToTop />
    <Routes>
      <Route element={<PublicLayout />}>
        <Route index element={<HomePage />} />
        <Route path="marketplace" element={<MarketplacePage />} />
        <Route path="categories/:code" element={<CategoryPage />} />
        <Route path="tickets/:ticketId" element={<TicketDetailPage />} />
        <Route path="workers" element={<WorkersPage />} />
        <Route path="workers/:workerId" element={<WorkerProfilePage />} />
        <Route path="how-it-works" element={<HowItWorksPage />} />
        <Route path="terms" element={<TermsPage />} />
        <Route path="privacy" element={<PrivacyPage />} />
        <Route element={<ProtectedRoute />}><Route path="account" element={<AccountPage />} /></Route>
        <Route path="forbidden" element={<ForbiddenPage />} />
      </Route>

      <Route path="login" element={<LoginPage />} />
      <Route path="register" element={<RegisterPage />} />

      <Route element={<ProtectedRoute />}>
        <Route path="app" element={<AppLanding />} />

        <Route path="app/client" element={<WorkspacePreference mode="client" />}>
          <Route index element={<ClientDashboardPage />} />
          <Route path="requests" element={<MyListingsPage kind="REQUEST" workspace="client" />} />
          <Route path="requests/new" element={<ListingComposerPage kind="REQUEST" workspace="client" />} />
          <Route path="requests/:ticketId" element={<ListingManagementPage kind="REQUEST" workspace="client" />} />
          <Route path="requests/:ticketId/edit" element={<ListingComposerPage kind="REQUEST" workspace="client" />} />
          <Route path="requests/:ticketId/applications" element={<ApplicationsPage workspace="client" direction="incoming" />} />
          <Route path="applications" element={<ApplicationsPage workspace="client" direction="outgoing" />} />
          <Route path="engagements" element={<EngagementListPage workspace="client" />} />
          <Route path="engagements/:engagementId" element={<EngagementDetailPage workspace="client" />} />
          <Route path="engagements/:engagementId/payment" element={<PaymentPage />} />
          <Route path="refunds" element={<RefundListPage />} />
          <Route path="refunds/:refundId" element={<RefundDetailPage />} />
          <Route path="reputation" element={<ReputationPage workspace="client" />} />
        </Route>

        <Route path="app/worker" element={<WorkspacePreference mode="worker" />}>
          <Route index element={<WorkerDashboardPage />} />
          <Route path="profile" element={<WorkerProfileSettingsPage />} />
          <Route path="offers" element={<MyListingsPage kind="OFFER" workspace="worker" />} />
          <Route path="offers/new" element={<ListingComposerPage kind="OFFER" workspace="worker" />} />
          <Route path="offers/:ticketId" element={<ListingManagementPage kind="OFFER" workspace="worker" />} />
          <Route path="offers/:ticketId/edit" element={<ListingComposerPage kind="OFFER" workspace="worker" />} />
          <Route path="offers/:ticketId/applications" element={<ApplicationsPage workspace="worker" direction="incoming" />} />
          <Route path="proposals" element={<ApplicationsPage workspace="worker" direction="outgoing" />} />
          <Route path="engagements" element={<EngagementListPage workspace="worker" />} />
          <Route path="engagements/:engagementId" element={<EngagementDetailPage workspace="worker" />} />
          <Route path="credentials" element={<CredentialsPage />} />
          <Route path="credentials/new" element={<CredentialNewPage />} />
          <Route path="earnings" element={<EarningsPage />} />
          <Route path="earnings/:settlementId" element={<SettlementDetailPage />} />
          <Route path="reputation" element={<ReputationPage workspace="worker" />} />
        </Route>
      </Route>

      <Route element={<ProtectedRoute admin />}>
        <Route path="admin" element={<WorkspaceLayout mode="admin" />}>
          <Route index element={<AdminOverviewPage />} />
          <Route path="credentials" element={<AdminCredentialsPage />} />
          <Route path="disputes" element={<AdminDisputesPage />} />
          <Route path="disputes/:engagementId" element={<AdminDisputeDetailPage />} />
          <Route path="finance" element={<AdminFinancePage />} />
          <Route path="finance/settlements/:settlementId" element={<SettlementDetailPage admin />} />
          <Route path="finance/batches" element={<AdminBatchesPage />} />
          <Route path="finance/refunds" element={<AdminRefundsPage />} />
          <Route path="finance/refunds/:refundId" element={<RefundDetailPage admin />} />
          <Route path="categories" element={<AdminCategoriesPage />} />
          <Route path="audit" element={<AdminAuditPage />} />
        </Route>
      </Route>

      <Route element={<PublicLayout />}><Route path="*" element={<NotFoundPage />} /></Route>
    </Routes>
  </>;
}
