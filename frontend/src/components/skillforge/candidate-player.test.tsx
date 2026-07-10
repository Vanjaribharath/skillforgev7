import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

const postMock = vi.fn();
const getMock = vi.fn();
vi.mock("@/lib/api-client", () => ({
  api: {
    get: (...args: unknown[]) => getMock(...args),
    post: (...args: unknown[]) => postMock(...args),
    put: (...args: unknown[]) => Promise.resolve({ data: {} }),
  },
}));

import { CandidatePlayer } from "./candidate-player";

function renderPlayer() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <CandidatePlayer />
    </QueryClientProvider>,
  );
}

describe("CandidatePlayer invitation flow", () => {
  beforeEach(() => {
    postMock.mockReset();
    getMock.mockReset();
    getMock.mockResolvedValue({ data: [] });
  });

  it("validates the token first, then starts the attempt using the validated invitationId (not the raw token)", async () => {
    postMock.mockImplementation((url: string, body?: any) => {
      if (url === "/candidate/link/validate") {
        expect(body).toEqual({ token: "raw-token-from-email" });
        return Promise.resolve({ data: { valid: true, invitationId: "real-invitation-uuid" } });
      }
      if (url === "/candidate/attempts/start/real-invitation-uuid") {
        return Promise.resolve({ data: { id: "attempt-1" } });
      }
      return Promise.reject(new Error(`Unexpected POST ${url}`));
    });

    renderPlayer();

    fireEvent.change(screen.getByPlaceholderText(/3f9c1a02/i), { target: { value: "raw-token-from-email" } });
    fireEvent.click(screen.getByRole("button", { name: /start assessment/i }));

    await waitFor(() => expect(postMock).toHaveBeenCalledWith("/candidate/attempts/start/real-invitation-uuid"));

    // Regression check: must validate before starting, in that order, and
    // must never pass the raw token directly to the start endpoint (that
    // was the actual bug -- the backend expects the invitation's internal
    // UUID there, not the secret token).
    const calls = postMock.mock.calls.map((c) => c[0]);
    expect(calls.indexOf("/candidate/link/validate")).toBeLessThan(calls.indexOf("/candidate/attempts/start/real-invitation-uuid"));
    expect(postMock).not.toHaveBeenCalledWith("/candidate/attempts/start/raw-token-from-email");
  });

  it("shows a clear message and never starts an attempt when the token is invalid/expired", async () => {
    postMock.mockImplementation((url: string) => {
      if (url === "/candidate/link/validate") {
        return Promise.resolve({ data: { valid: false } });
      }
      return Promise.reject(new Error(`Unexpected POST ${url}`));
    });

    renderPlayer();
    fireEvent.change(screen.getByPlaceholderText(/3f9c1a02/i), { target: { value: "expired-token" } });
    fireEvent.click(screen.getByRole("button", { name: /start assessment/i }));

    await waitFor(() =>
      expect(screen.getByText(/invalid, expired, or has already been used/i)).toBeInTheDocument(),
    );
    expect(postMock).not.toHaveBeenCalledWith(expect.stringContaining("/candidate/attempts/start/"));
  });

  it("surfaces the backend's real error message when validation itself fails", async () => {
    postMock.mockImplementation((url: string) => {
      if (url === "/candidate/link/validate") {
        return Promise.reject({ response: { data: { error: "Invitation has already been used" } } });
      }
      return Promise.reject(new Error(`Unexpected POST ${url}`));
    });

    renderPlayer();
    fireEvent.change(screen.getByPlaceholderText(/3f9c1a02/i), { target: { value: "used-token" } });
    fireEvent.click(screen.getByRole("button", { name: /start assessment/i }));

    await waitFor(() => expect(screen.getByText(/Failed to start: Invitation has already been used/i)).toBeInTheDocument());
  });
});
