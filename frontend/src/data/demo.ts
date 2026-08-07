import type {
  Application,
  Category,
  Credential,
  Engagement,
  PageResponse,
  Refund,
  Review,
  Settlement,
  TicketDetail,
  TicketSummary,
  UserResponse,
  WorkerProfile,
} from "../types";

const now = "2026-08-05T15:00:00Z";
const later = "2026-08-12T15:00:00Z";
const image = (name: string) => ({ thumb: `/images/${name}`, large: `/images/${name}` });

export const demoCategories: Category[] = [
  { id: 1, code: "CLEANING", name: "Cleaning", description: "Home and office cleaning", icon: "sparkles", requiredCredential: null, active: true, parentId: null, children: [] },
  { id: 2, code: "MOVING", name: "Moving", description: "Packing and local moving help", icon: "truck", requiredCredential: "DRIVER_LICENSE", active: true, parentId: null, children: [] },
  { id: 3, code: "PLUMBING", name: "Plumbing", description: "Repairs, fixtures, and leaks", icon: "wrench", requiredCredential: null, active: true, parentId: null, children: [] },
  { id: 4, code: "TECH_SUPPORT", name: "Tech support", description: "Home technology and networking", icon: "laptop", requiredCredential: null, active: true, parentId: null, children: [] },
  { id: 5, code: "ELECTRICAL", name: "Electrical", description: "Licensed electrical work", icon: "zap", requiredCredential: "ELECTRICAL_LICENSE", active: true, parentId: null, children: [] },
  { id: 6, code: "CHILDCARE", name: "Childcare", description: "Trusted family support", icon: "heart", requiredCredential: "BACKGROUND_CHECK", active: true, parentId: null, children: [] },
];

const baseTicket = {
  currency: "USD",
  status: "OPEN" as const,
  imageCount: 1,
  viewCount: 86,
  serviceWindowStart: "2026-08-10T14:00:00Z",
  serviceWindowEnd: "2026-08-17T22:00:00Z",
  expiresAt: later,
  createdAt: now,
  updatedAt: now,
};

export const demoTickets: TicketSummary[] = [
  {
    ...baseTicket,
    id: 101,
    kind: "OFFER",
    title: "Weekly Home Refresh",
    pricingMode: "FIXED",
    price: "85.00",
    budgetMin: null,
    budgetMax: null,
    locationMode: "ON_SITE",
    city: "Chicago",
    coverImage: image("home-cleaning.jpg"),
    applicationCount: 8,
    author: { id: 7, name: "Sofia Reyes", avatarUrl: null, rating: "4.90" },
    category: { id: 1, code: "CLEANING", name: "Cleaning", icon: "sparkles" },
    worker: { id: 7, displayName: "FreshStart Cleaning", headline: "Thoughtful home care, every week", avatarUrl: null, rating: "4.90", reviewCount: 142, verified: true },
  },
  {
    ...baseTicket,
    id: 102,
    kind: "OFFER",
    title: "Emergency Plumbing Visit",
    pricingMode: "OPEN_BID",
    price: null,
    budgetMin: null,
    budgetMax: null,
    locationMode: "ON_SITE",
    city: "Chicago",
    coverImage: image("plumbing-leak.jpg"),
    applicationCount: 5,
    author: { id: 10, name: "Oliver Grant", avatarUrl: null, rating: "5.00" },
    category: { id: 3, code: "PLUMBING", name: "Plumbing", icon: "wrench" },
    worker: { id: 10, displayName: "TechHand Solutions", headline: "Technology and household fixes, explained clearly", avatarUrl: null, rating: "5.00", reviewCount: 24, verified: true },
  },
  {
    ...baseTicket,
    id: 103,
    kind: "OFFER",
    title: "Home Wi-Fi Optimization",
    pricingMode: "BUDGET_RANGE",
    price: null,
    budgetMin: "90.00",
    budgetMax: "180.00",
    locationMode: "HYBRID",
    city: "Chicago",
    coverImage: image("tech-support.jpg"),
    applicationCount: 4,
    author: { id: 10, name: "Oliver Grant", avatarUrl: null, rating: "5.00" },
    category: { id: 4, code: "TECH_SUPPORT", name: "Tech support", icon: "laptop" },
    worker: { id: 10, displayName: "TechHand Solutions", headline: "Home technology without the jargon", avatarUrl: null, rating: "5.00", reviewCount: 24, verified: true },
  },
  {
    ...baseTicket,
    id: 104,
    kind: "OFFER",
    title: "Small Apartment Move",
    pricingMode: "BUDGET_RANGE",
    price: null,
    budgetMin: "180.00",
    budgetMax: "300.00",
    locationMode: "ON_SITE",
    city: "Chicago",
    coverImage: image("moving-help.jpg"),
    applicationCount: 2,
    author: { id: 8, name: "Noah Williams", avatarUrl: null, rating: "4.80" },
    category: { id: 2, code: "MOVING", name: "Moving", icon: "truck" },
    worker: { id: 8, displayName: "SwiftMove Local", headline: "Careful, efficient local moves", avatarUrl: null, rating: "4.80", reviewCount: 110, verified: true },
  },
  {
    ...baseTicket,
    id: 201,
    kind: "REQUEST",
    title: "Fix Leaky Kitchen Faucet",
    pricingMode: "BUDGET_RANGE",
    price: null,
    budgetMin: "100.00",
    budgetMax: "150.00",
    locationMode: "ON_SITE",
    city: "Chicago",
    coverImage: image("plumbing-leak.jpg"),
    applicationCount: 6,
    author: { id: 2, name: "John Carter", avatarUrl: null, rating: "4.80" },
    category: { id: 3, code: "PLUMBING", name: "Plumbing", icon: "wrench" },
    worker: null,
  },
  {
    ...baseTicket,
    id: 202,
    kind: "REQUEST",
    title: "Set Up New Router",
    pricingMode: "BUDGET_RANGE",
    price: null,
    budgetMin: "50.00",
    budgetMax: "90.00",
    locationMode: "HYBRID",
    city: "Chicago",
    coverImage: image("tech-support.jpg"),
    applicationCount: 3,
    author: { id: 3, name: "Jane Park", avatarUrl: null, rating: "5.00" },
    category: { id: 4, code: "TECH_SUPPORT", name: "Tech support", icon: "laptop" },
    worker: null,
  },
  {
    ...baseTicket,
    id: 203,
    kind: "REQUEST",
    title: "Help Move a Studio",
    pricingMode: "FIXED",
    price: "240.00",
    budgetMin: null,
    budgetMax: null,
    locationMode: "ON_SITE",
    city: "Chicago",
    coverImage: image("moving-help.jpg"),
    applicationCount: 5,
    author: { id: 3, name: "Jane Park", avatarUrl: null, rating: "5.00" },
    category: { id: 2, code: "MOVING", name: "Moving", icon: "truck" },
    worker: null,
  },
  {
    ...baseTicket,
    id: 204,
    kind: "REQUEST",
    title: "Move-out Deep Clean",
    pricingMode: "OPEN_BID",
    price: null,
    budgetMin: null,
    budgetMax: null,
    locationMode: "ON_SITE",
    city: "Evanston",
    coverImage: image("home-cleaning.jpg"),
    applicationCount: 7,
    author: { id: 2, name: "John Carter", avatarUrl: null, rating: "4.80" },
    category: { id: 1, code: "CLEANING", name: "Cleaning", icon: "sparkles" },
    worker: null,
  },
];

export const demoTicketDetails: TicketDetail[] = demoTickets.map((ticket) => ({
  ...ticket,
  description:
    ticket.kind === "OFFER"
      ? "Clear, reliable service with transparent pricing. We will confirm scope and timing before work begins."
      : "I am looking for a dependable local professional. Please share your approach, proposed price, and availability.",
  address: ticket.locationMode === "REMOTE" ? null : "123 Maple St, Chicago, IL 60601",
  latitude: "41.8781",
  longitude: "-87.6298",
  estimatedDurationMinutes: 120,
  images: ticket.coverImage ? [{ id: ticket.id * 10, image: ticket.coverImage, position: 0, caption: null }] : [],
}));

export const demoWorkers: WorkerProfile[] = [
  { id: 7, userId: 7, displayName: "FreshStart Cleaning", headline: "Thoughtful home care, every week", description: "Reliable home and office cleaning with careful attention to the details that make a space feel good.", address: "Chicago, IL", rating: "4.90", reviewCount: 142, verified: true, completedJobs: 220, serviceRadiusKm: "25", recentWork: [{ ticketId: 101, ticketTitle: "Weekly Home Refresh", image: image("home-cleaning.jpg") }], createdAt: now },
  { id: 8, userId: 8, displayName: "SwiftMove Local", headline: "Careful, efficient local moves", description: "Friendly local moving support, from one-room pickups to full apartment moves.", address: "Chicago, IL", rating: "4.80", reviewCount: 110, verified: true, completedJobs: 160, serviceRadiusKm: "35", recentWork: [{ ticketId: 104, ticketTitle: "Small Apartment Move", image: image("moving-help.jpg") }], createdAt: now },
  { id: 10, userId: 10, displayName: "TechHand Solutions", headline: "Technology and household fixes, explained clearly", description: "I help with everyday technology and household issues so things work smoothly again.", address: "Chicago, IL", rating: "5.00", reviewCount: 24, verified: true, completedJobs: 38, serviceRadiusKm: "30", recentWork: [{ ticketId: 102, ticketTitle: "Emergency Plumbing Visit", image: image("plumbing-leak.jpg") }, { ticketId: 103, ticketTitle: "Home Wi-Fi Optimization", image: image("tech-support.jpg") }], createdAt: now },
  { id: 6, userId: 6, displayName: "BrightWire Electric", headline: "Licensed residential electrical support", description: "Clear estimates and careful residential electrical work.", address: "Chicago, IL", rating: "4.90", reviewCount: 96, verified: true, completedJobs: 180, serviceRadiusKm: "40", recentWork: [], createdAt: now },
];

export const demoUser: UserResponse = {
  id: 2,
  name: "John Carter",
  email: "john@example.com",
  phone: "312-555-0188",
  role: "USER",
  capabilities: ["CLIENT", "WORKER"],
  status: "ACTIVE",
  clientRating: "4.80",
  clientReviewCount: 12,
  avatarUrl: null,
  createdAt: "2025-11-12T15:00:00Z",
};

export const demoAdmin: UserResponse = { ...demoUser, id: 1, name: "System Admin", email: "admin@marketplace.com", role: "ADMIN" };

export const demoApplications: Application[] = [
  { id: 901, ticketId: 201, ticketKind: "REQUEST", ticketTitle: "Fix Leaky Kitchen Faucet", applicantId: 10, applicantName: "Oliver Grant", applicantAvatarUrl: null, proposedAmount: "120.00", message: "I can inspect the leak and complete the repair cleanly.", proposedStart: later, proposedEnd: "2026-08-12T17:00:00Z", status: "PENDING", engagementId: null, createdAt: now, updatedAt: now },
  { id: 902, ticketId: 203, ticketKind: "REQUEST", ticketTitle: "Help Move a Studio", applicantId: 8, applicantName: "Noah Williams", applicantAvatarUrl: null, proposedAmount: "240.00", message: "Two movers and a van are available.", proposedStart: later, proposedEnd: "2026-08-12T20:00:00Z", status: "ACCEPTED", engagementId: 1005, createdAt: now, updatedAt: now },
  { id: 903, ticketId: 101, ticketKind: "OFFER", ticketTitle: "Weekly Home Refresh", applicantId: 2, applicantName: "John Carter", applicantAvatarUrl: null, proposedAmount: "85.00", message: "A Friday morning visit would be ideal.", proposedStart: later, proposedEnd: "2026-08-12T17:00:00Z", status: "PENDING", engagementId: null, createdAt: now, updatedAt: now },
];

export const demoEngagements: Engagement[] = [
  { id: 1001, ticketId: 201, applicationId: 901, clientId: 2, clientName: "John Carter", workerId: 10, workerUserId: 10, workerDisplayName: "TechHand Solutions", status: "ACCEPTED", amount: "120.00", notes: "Please inspect and repair the leaking connection.", scheduledStart: later, scheduledEnd: "2026-08-12T17:00:00Z", acceptedAt: now, fundedAt: null, startedAt: null, deliveredAt: null, approvedAt: null, completedAt: null, disputedAt: null, disputeReason: null, cancelledAt: null, cancellationReason: null, deliverableCount: 0, refundSummary: null, createdAt: now, updatedAt: now },
  { id: 1002, ticketId: 202, applicationId: 904, clientId: 2, clientName: "John Carter", workerId: 10, workerUserId: 10, workerDisplayName: "TechHand Solutions", status: "FUNDED", amount: "95.00", notes: "Router is already unpacked.", scheduledStart: later, scheduledEnd: "2026-08-12T17:00:00Z", acceptedAt: now, fundedAt: now, startedAt: null, deliveredAt: null, approvedAt: null, completedAt: null, disputedAt: null, disputeReason: null, cancelledAt: null, cancellationReason: null, deliverableCount: 0, refundSummary: null, createdAt: now, updatedAt: now },
  { id: 1003, ticketId: 103, applicationId: 905, clientId: 3, clientName: "Jane Park", workerId: 10, workerUserId: 10, workerDisplayName: "TechHand Solutions", status: "IN_PROGRESS", amount: "120.00", notes: "Please optimize coverage upstairs.", scheduledStart: later, scheduledEnd: "2026-08-12T17:00:00Z", acceptedAt: now, fundedAt: now, startedAt: now, deliveredAt: null, approvedAt: null, completedAt: null, disputedAt: null, disputeReason: null, cancelledAt: null, cancellationReason: null, deliverableCount: 2, refundSummary: null, createdAt: now, updatedAt: now },
  { id: 1004, ticketId: 101, applicationId: 906, clientId: 2, clientName: "John Carter", workerId: 7, workerUserId: 7, workerDisplayName: "FreshStart Cleaning", status: "DELIVERED", amount: "85.00", notes: "Focus on kitchen and living room.", scheduledStart: later, scheduledEnd: "2026-08-12T17:00:00Z", acceptedAt: now, fundedAt: now, startedAt: now, deliveredAt: now, approvedAt: null, completedAt: null, disputedAt: null, disputeReason: null, cancelledAt: null, cancellationReason: null, deliverableCount: 3, refundSummary: null, createdAt: now, updatedAt: now },
  { id: 1005, ticketId: 203, applicationId: 902, clientId: 3, clientName: "Jane Park", workerId: 8, workerUserId: 8, workerDisplayName: "SwiftMove Local", status: "COMPLETED", amount: "240.00", notes: "Use the rear loading entrance.", scheduledStart: later, scheduledEnd: "2026-08-12T20:00:00Z", acceptedAt: now, fundedAt: now, startedAt: now, deliveredAt: now, approvedAt: now, completedAt: now, disputedAt: null, disputeReason: null, cancelledAt: null, cancellationReason: null, deliverableCount: 3, refundSummary: null, createdAt: now, updatedAt: now },
  { id: 1006, ticketId: 204, applicationId: 907, clientId: 2, clientName: "John Carter", workerId: 7, workerUserId: 7, workerDisplayName: "FreshStart Cleaning", status: "DISPUTED", amount: "160.00", notes: null, scheduledStart: later, scheduledEnd: "2026-08-12T20:00:00Z", acceptedAt: now, fundedAt: now, startedAt: now, deliveredAt: now, approvedAt: null, completedAt: null, disputedAt: now, disputeReason: "The agreed rooms were not all completed.", cancelledAt: null, cancellationReason: null, deliverableCount: 2, refundSummary: null, createdAt: now, updatedAt: now },
];

export const demoCredentials: Credential[] = [
  { id: 501, workerId: 10, workerUserId: 10, workerDisplayName: "TechHand Solutions", type: "ELECTRICAL_LICENSE", status: "VERIFIED", credentialNumber: "ELEC-112233", document: { url: "/images/tech-support.jpg", managed: true, contentType: "image/jpeg", byteSize: 245000, width: 1400, height: 933 }, issuedAt: "2021-06-05", expiresAt: "2027-01-02", rejectionReason: null, reviewedByUserId: 1, reviewedAt: now, createdAt: now, updatedAt: now },
  { id: 502, workerId: 10, workerUserId: 10, workerDisplayName: "TechHand Solutions", type: "DRIVER_LICENSE", status: "PENDING", credentialNumber: "D123-4567-8901", document: null, issuedAt: "2018-07-12", expiresAt: "2028-07-12", rejectionReason: null, reviewedByUserId: null, reviewedAt: null, createdAt: now, updatedAt: now },
  { id: 503, workerId: 10, workerUserId: 10, workerDisplayName: "TechHand Solutions", type: "BACKGROUND_CHECK", status: "REJECTED", credentialNumber: "BG-778899", document: null, issuedAt: "2025-02-01", expiresAt: null, rejectionReason: "Document is blurry and unreadable.", reviewedByUserId: 1, reviewedAt: now, createdAt: now, updatedAt: now },
];

export const demoRefunds: Refund[] = [
  { id: 701, engagementId: 1010, paymentId: 801, amount: "95.00", reason: "Engagement cancelled after funding", status: "COMPLETED", providerRefundId: "re_demo_701", providerStatus: "succeeded", failureMessage: null, refundedAt: now, createdAt: now, updatedAt: now },
  { id: 702, engagementId: 1011, paymentId: 802, amount: "140.00", reason: "Dispute resolved for client", status: "FAILED", providerRefundId: "re_demo_702", providerStatus: "failed", failureMessage: "Insufficient balance in source account.", refundedAt: null, createdAt: now, updatedAt: now },
];

export const demoSettlements: Settlement[] = [
  { id: 601, engagementId: 1005, totalAmount: "240.00", platformFee: "24.00", workerPayout: "216.00", status: "COMPLETED", settledAt: now, createdAt: now },
  { id: 602, engagementId: 1008, totalAmount: "120.00", platformFee: "12.00", workerPayout: "108.00", status: "PENDING", settledAt: null, createdAt: now },
  { id: 603, engagementId: 1009, totalAmount: "95.00", platformFee: "9.50", workerPayout: "85.50", status: "FAILED", settledAt: null, createdAt: now },
];

export const demoReviews: Review[] = [
  { id: 301, engagementId: 1005, direction: "CLIENT_TO_WORKER", rating: 5, comment: "Clear communication and careful work. Everything arrived safely.", reviewer: { id: 3, name: "Jane Park", avatarUrl: null }, reviewee: { id: 8, name: "SwiftMove Local", avatarUrl: null }, createdAt: now },
  { id: 302, engagementId: 1005, direction: "WORKER_TO_CLIENT", rating: 5, comment: "Great directions and everything was ready when we arrived.", reviewer: { id: 8, name: "Noah Williams", avatarUrl: null }, reviewee: { id: 3, name: "Jane Park", avatarUrl: null }, createdAt: now },
];

export function asPage<T>(items: T[], page = 0, size = 20): PageResponse<T> {
  const start = page * size;
  const pageItems = items.slice(start, start + size);
  return {
    items: pageItems,
    page,
    size,
    totalElements: items.length,
    totalPages: Math.max(1, Math.ceil(items.length / size)),
    hasNext: start + size < items.length,
  };
}
