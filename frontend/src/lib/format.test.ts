import { describe, expect, it } from "vitest";
import { money, ticketPrice, titleCase } from "./format";

describe("marketplace formatters", () => {
  it("formats decimal-string money without concatenating strings", () => {
    expect(money("125.50")).toBe("$125.50");
  });

  it("renders every pricing model", () => {
    expect(ticketPrice({ pricingMode: "FIXED", price: "85.00", budgetMin: null, budgetMax: null, currency: "USD" })).toBe("$85.00 fixed");
    expect(ticketPrice({ pricingMode: "BUDGET_RANGE", price: null, budgetMin: "100.00", budgetMax: "180.00", currency: "USD" })).toBe("$100.00–$180.00");
    expect(ticketPrice({ pricingMode: "OPEN_BID", price: null, budgetMin: null, budgetMax: null, currency: "USD" })).toBe("Open bid");
  });

  it("turns API enum keys into readable labels", () => {
    expect(titleCase("IN_PROGRESS")).toBe("In Progress");
  });
});
