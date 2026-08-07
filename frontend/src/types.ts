export type Capability = "CLIENT" | "WORKER";
export type Role = "USER" | "ADMIN";
export type TicketKind = "OFFER" | "REQUEST";
export type TicketStatus = "DRAFT" | "OPEN" | "MATCHED" | "CLOSED" | "CANCELLED" | "EXPIRED";
export type PricingMode = "FIXED" | "BUDGET_RANGE" | "OPEN_BID";
export type LocationMode = "ON_SITE" | "REMOTE" | "HYBRID";
export type ApplicationStatus = "PENDING" | "ACCEPTED" | "REJECTED" | "WITHDRAWN" | "EXPIRED";
export type EngagementStatus =
  | "ACCEPTED"
  | "FUNDED"
  | "IN_PROGRESS"
  | "DELIVERED"
  | "COMPLETED"
  | "DISPUTED"
  | "CANCELLED"
  | "REFUNDED";
export type CredentialType = "ELECTRICAL_LICENSE" | "DRIVER_LICENSE" | "BACKGROUND_CHECK";
export type CredentialStatus = "PENDING" | "VERIFIED" | "REJECTED" | "EXPIRED";
export type FinancialStatus = "PENDING" | "PROCESSING" | "COMPLETED" | "FAILED";

export interface ApiProblem {
  code: string;
  field?: string;
  details?: unknown;
}

export interface ApiEnvelope<T> {
  success: boolean;
  message?: string;
  data?: T;
  error?: ApiProblem;
  timestamp: string;
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

export interface AuthResponse {
  userId: number;
  email: string;
  name: string;
  role: Role;
  capabilities: Capability[];
  accessToken: string;
  refreshToken: string;
  tokenType: "Bearer";
  expiresIn: number;
}

export interface UserResponse {
  id: number;
  name: string;
  email: string;
  phone: string | null;
  role: Role;
  capabilities: Capability[];
  status: "ACTIVE" | "INACTIVE" | "SUSPENDED";
  clientRating: string;
  clientReviewCount: number;
  avatarUrl: string | null;
  createdAt: string;
}

export interface ImageVariants {
  thumb: string;
  large: string;
}

export interface Category {
  id: number;
  code: string;
  name: string;
  description: string | null;
  icon: string | null;
  requiredCredential: CredentialType | null;
  active: boolean;
  parentId: number | null;
  children: Category[];
  createdAt?: string;
  updatedAt?: string;
}

export interface TicketSummary {
  id: number;
  kind: TicketKind;
  title: string;
  pricingMode: PricingMode;
  price: string | null;
  budgetMin: string | null;
  budgetMax: string | null;
  currency: string;
  locationMode: LocationMode;
  city: string | null;
  status: TicketStatus;
  coverImage: ImageVariants | null;
  imageCount: number;
  viewCount: number;
  applicationCount: number;
  serviceWindowStart: string | null;
  serviceWindowEnd: string | null;
  expiresAt: string | null;
  createdAt: string;
  updatedAt: string;
  author: { id: number; name: string; avatarUrl: string | null; rating: string };
  category: { id: number; code: string; name: string; icon: string | null };
  worker: {
    id: number;
    displayName: string;
    headline: string | null;
    avatarUrl: string | null;
    rating: string;
    reviewCount: number;
    verified: boolean;
  } | null;
}

export interface TicketDetail extends TicketSummary {
  description: string | null;
  address: string | null;
  latitude: string | null;
  longitude: string | null;
  estimatedDurationMinutes: number | null;
  images: Array<{ id: number; image: ImageVariants; position: number; caption: string | null }>;
}

export interface WorkerProfile {
  id: number;
  userId: number;
  displayName: string;
  headline: string | null;
  description: string | null;
  address: string | null;
  rating: string;
  reviewCount: number;
  verified: boolean;
  completedJobs: number;
  serviceRadiusKm: string | null;
  recentWork: Array<{ ticketId: number; ticketTitle: string; image: ImageVariants }>;
  createdAt: string;
}

export interface Application {
  id: number;
  ticketId: number;
  ticketKind: TicketKind;
  ticketTitle: string;
  applicantId: number;
  applicantName: string;
  applicantAvatarUrl: string | null;
  proposedAmount: string;
  message: string | null;
  proposedStart: string | null;
  proposedEnd: string | null;
  status: ApplicationStatus;
  engagementId: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface RefundSummary {
  id: number;
  status: FinancialStatus;
  amount: string;
  failureMessage?: string | null;
  refundedAt?: string | null;
  updatedAt: string;
}

export interface Engagement {
  id: number;
  ticketId: number;
  applicationId: number | null;
  clientId: number;
  clientName: string;
  workerId: number;
  workerUserId: number;
  workerDisplayName: string;
  status: EngagementStatus;
  amount: string;
  notes: string | null;
  scheduledStart: string | null;
  scheduledEnd: string | null;
  acceptedAt: string;
  fundedAt: string | null;
  startedAt: string | null;
  deliveredAt: string | null;
  approvedAt: string | null;
  completedAt: string | null;
  disputedAt: string | null;
  disputeReason: string | null;
  cancelledAt: string | null;
  cancellationReason: string | null;
  deliverableCount: number;
  refundSummary: RefundSummary | null;
  createdAt: string;
  updatedAt: string;
}

export interface Deliverable {
  id: number;
  image: ImageVariants;
  note: string | null;
  position: number;
  submittedBy: number;
  createdAt: string;
}

export interface PaymentConfig {
  gateway: "mock" | "stripe" | string;
  publishableKey: string | null;
}

export interface Payment {
  paymentId: number;
  engagementId: number;
  requestId: string;
  amount: string;
  currency: string;
  status: "PENDING" | "SUCCEEDED" | "FAILED" | "REFUNDED";
  paidAt: string | null;
  paymentIntentId: string;
  clientSecret?: string;
  providerStatus: string | null;
  failureMessage: string | null;
  alreadyPaid: boolean;
  requestIdMatched: boolean;
}

export interface Credential {
  id: number;
  workerId: number;
  workerUserId: number;
  workerDisplayName: string;
  type: CredentialType;
  status: CredentialStatus;
  credentialNumber: string | null;
  document: {
    url: string;
    managed: boolean;
    contentType: string | null;
    byteSize: number | null;
    width: number | null;
    height: number | null;
  } | null;
  issuedAt: string | null;
  expiresAt: string | null;
  rejectionReason: string | null;
  reviewedByUserId: number | null;
  reviewedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface Review {
  id: number;
  engagementId: number;
  direction: "CLIENT_TO_WORKER" | "WORKER_TO_CLIENT";
  rating: number;
  comment: string | null;
  reviewer: { id: number; name: string; avatarUrl: string | null };
  reviewee: { id: number; name: string; avatarUrl: string | null };
  createdAt: string;
}

export interface Refund {
  id: number;
  engagementId: number;
  paymentId: number;
  amount: string;
  reason: string;
  status: FinancialStatus;
  providerRefundId: string | null;
  providerStatus: string | null;
  failureMessage: string | null;
  refundedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface Settlement {
  id: number;
  engagementId: number;
  totalAmount: string;
  platformFee: string;
  workerPayout: string;
  status: FinancialStatus;
  settledAt: string | null;
  createdAt: string;
}

export interface SettlementSummary {
  totalEarnings: string;
  completedAmount: string;
  pendingAmount: string;
  totalCount: number;
  completedCount: number;
  pendingCount: number;
  failedCount: number;
}

export interface AuditLog {
  id: number;
  entityType: string;
  entityId: number;
  action: string;
  actorType: "USER" | "ADMIN" | "SYSTEM" | string;
  actorId: number | null;
  details: string | null;
  createdAt: string;
}

export interface SettlementBatch {
  id: number;
  batchId: string;
  status: FinancialStatus;
  totalCount: number;
  successCount: number;
  failedCount: number;
  totalAmount: string;
  startedAt: string;
  completedAt: string | null;
  createdAt: string;
}
