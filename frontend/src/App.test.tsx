import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import App from "./App";
import { AuthProvider } from "./auth/AuthContext";
import { tokenStore } from "./lib/api";
import type { AuthResponse } from "./types";

function signIn(role: "USER" | "ADMIN" = "USER") {
  const auth: AuthResponse = {
    userId: role === "ADMIN" ? 1 : 2,
    email: role === "ADMIN" ? "admin@marketplace.com" : "john@example.com",
    name: role === "ADMIN" ? "System Admin" : "John Carter",
    role,
    capabilities: ["CLIENT", "WORKER"],
    accessToken: "demo-access-token",
    refreshToken: "demo-refresh-token",
    tokenType: "Bearer",
    expiresIn: 7200,
  };
  tokenStore.save(auth);
}

describe("public application shell", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("API offline")));
  });

  it("renders the approved marketplace home and offline demo state", async () => {
    render(<MemoryRouter initialEntries={["/"]}><AuthProvider><App /></AuthProvider></MemoryRouter>);
    expect(screen.getByRole("heading", { name: /Find trusted help/i })).toBeInTheDocument();
    expect(await screen.findByText(/Demo data is shown because the API is offline/i)).toBeInTheDocument();
    expect(screen.getAllByRole("link", { name: /Service Marketplace home/i })).toHaveLength(2);
  });

  it("routes a signed-in member into the client workspace", async () => {
    signIn();
    render(<MemoryRouter initialEntries={["/app/client"]}><AuthProvider><App /></AuthProvider></MemoryRouter>);
    expect(await screen.findByRole("heading", { name: /Good work starts with a clear request/i })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /My requests/i })).toBeInTheDocument();
  });

  it("protects and renders the admin operations workspace", async () => {
    signIn("ADMIN");
    render(<MemoryRouter initialEntries={["/admin"]}><AuthProvider><App /></AuthProvider></MemoryRouter>);
    expect(await screen.findByRole("heading", { name: /Marketplace oversight/i })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Credential review/i })).toBeInTheDocument();
  });
});
