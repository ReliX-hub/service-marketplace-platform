import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { Modal } from "./ui";

describe("Modal", () => {
  it("closes with Escape and exposes an accessible dialog name", () => {
    const close = vi.fn();
    render(<Modal title="Confirm action" open onClose={close}><p>Review the result.</p></Modal>);
    expect(screen.getByRole("dialog", { name: "Confirm action" })).toBeInTheDocument();
    fireEvent.keyDown(document, { key: "Escape" });
    expect(close).toHaveBeenCalledOnce();
  });
});
