import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";

const replaceMock = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: replaceMock }),
}));

const postMock = vi.fn();
vi.mock("@/lib/api-client", () => ({
  api: { post: (...args: unknown[]) => postMock(...args) },
}));

import LoginPage from "./page";

describe("LoginPage", () => {
  beforeEach(() => {
    postMock.mockReset();
    replaceMock.mockReset();
    window.localStorage.clear();
  });

  it("surfaces the backend's real error message instead of a generic one when the API returns a proper error body", async () => {
    postMock.mockRejectedValueOnce({ response: { data: { error: "Account is not active" } } });
    render(<LoginPage />);

    fireEvent.change(screen.getByPlaceholderText("you@organization.com"), { target: { value: "admin@apex.example" } });
    fireEvent.change(screen.getByPlaceholderText("••••••••"), { target: { value: "Demo@12345" } });
    fireEvent.click(screen.getByRole("button", { name: /sign in/i }));

    expect(await screen.findByText("Account is not active")).toBeInTheDocument();
    expect(replaceMock).not.toHaveBeenCalled();
  });

  it("falls back to a generic message only when the error has no parseable response body (e.g. a network/decoding failure)", async () => {
    postMock.mockRejectedValueOnce(new Error("network error"));
    render(<LoginPage />);

    fireEvent.change(screen.getByPlaceholderText("you@organization.com"), { target: { value: "admin@apex.example" } });
    fireEvent.change(screen.getByPlaceholderText("••••••••"), { target: { value: "Demo@12345" } });
    fireEvent.click(screen.getByRole("button", { name: /sign in/i }));

    expect(await screen.findByText("Invalid email or password.")).toBeInTheDocument();
  });

  it("redirects staff users to / and candidates to /candidate on success", async () => {
    postMock.mockResolvedValueOnce({
      data: { accessToken: "tok", user: { id: "1", organizationId: "org1", email: "admin@apex.example", fullName: "Admin", role: "ORG_ADMIN", status: "ACTIVE" } },
    });
    render(<LoginPage />);

    fireEvent.change(screen.getByPlaceholderText("you@organization.com"), { target: { value: "admin@apex.example" } });
    fireEvent.change(screen.getByPlaceholderText("••••••••"), { target: { value: "Demo@12345" } });
    fireEvent.click(screen.getByRole("button", { name: /sign in/i }));

    await waitFor(() => expect(replaceMock).toHaveBeenCalledWith("/"));
  });

  it("logs in with one click via the Trainer demo button, using the shared demo password", async () => {
    postMock.mockResolvedValueOnce({
      data: { accessToken: "tok", user: { id: "2", organizationId: "org1", email: "trainer@apex.example", fullName: "Trainer", role: "TRAINER", status: "ACTIVE" } },
    });
    render(<LoginPage />);

    fireEvent.click(screen.getByRole("button", { name: "Log in as Trainer" }));

    await waitFor(() => expect(postMock).toHaveBeenCalledWith("/auth/login", { email: "trainer@apex.example", password: "Demo@12345" }));
    await waitFor(() => expect(replaceMock).toHaveBeenCalledWith("/"));
  });

  it("routes the Candidate demo button to /candidate, not the staff dashboard", async () => {
    postMock.mockResolvedValueOnce({
      data: { accessToken: "tok", user: { id: "3", organizationId: "org1", email: "candidate@apex.example", fullName: "Candidate", role: "CANDIDATE", status: "ACTIVE" } },
    });
    render(<LoginPage />);

    fireEvent.click(screen.getByRole("button", { name: "Log in as Candidate" }));

    await waitFor(() => expect(replaceMock).toHaveBeenCalledWith("/candidate"));
  });
});
