import type { LocationMode, PricingMode, TicketSummary } from "../types";

export function money(value: string | number | null | undefined, currency = "USD") {
  if (value === null || value === undefined || value === "") return "—";
  return new Intl.NumberFormat("en-US", { style: "currency", currency }).format(Number(value));
}

export function ticketPrice(ticket: Pick<TicketSummary, "pricingMode" | "price" | "budgetMin" | "budgetMax" | "currency">) {
  if (ticket.pricingMode === "OPEN_BID") return "Open bid";
  if (ticket.pricingMode === "FIXED") return `${money(ticket.price, ticket.currency)} fixed`;
  return `${money(ticket.budgetMin, ticket.currency)}–${money(ticket.budgetMax, ticket.currency)}`;
}

export function date(value: string | null | undefined, options?: Intl.DateTimeFormatOptions) {
  if (!value) return "—";
  return new Intl.DateTimeFormat("en-US", options ?? { month: "short", day: "numeric", year: "numeric" }).format(new Date(value));
}

export function dateTime(value: string | null | undefined) {
  return date(value, { month: "short", day: "numeric", hour: "numeric", minute: "2-digit" });
}

export function initials(name: string) {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((word) => word[0]?.toUpperCase())
    .join("");
}

export function titleCase(value: string) {
  return value.toLowerCase().replace(/_/g, " ").replace(/\b\w/g, (char) => char.toUpperCase());
}

export const locationLabels: Record<LocationMode, string> = {
  ON_SITE: "On-site",
  REMOTE: "Remote",
  HYBRID: "Hybrid",
};

export const pricingLabels: Record<PricingMode, string> = {
  FIXED: "Fixed",
  BUDGET_RANGE: "Budget range",
  OPEN_BID: "Open bid",
};
