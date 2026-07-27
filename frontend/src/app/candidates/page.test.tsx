import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

vi.mock("@/store/use-organization-store", () => ({
  useOrganizationStore: () => ({ organizationId: "org-1" }),
}));

const getMock = vi.fn();
const postMock = vi.fn();
vi.mock("@/lib/api-client", () => ({
  api: {
    get: (...args: unknown[]) => getMock(...args),
    post: (...args: unknown[]) => postMock(...args),
  },
}));

import CandidatesPage from "./page";

function renderWithQueryClient() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <CandidatesPage />
    </QueryClientProvider>,
  );
}

describe("Candidates page list states", () => {
  beforeEach(() => {
    getMock.mockReset();
  });

  it("shows a loading state before the request resolves, not an empty list", async () => {
    getMock.mockImplementation(() => new Promise(() => {})); // never resolves
    renderWithQueryClient();
    expect(screen.getAllByText(/loading candidates/i).length).toBeGreaterThan(0);
    expect(screen.queryByText(/no candidates found/i)).not.toBeInTheDocument();
  });

  it("shows a real error state on API failure -- never silently renders as zero candidates", async () => {
    getMock.mockRejectedValue({
      response: { status: 500, data: { error: "Internal server error" } },
      message: "Request failed with status code 500",
    });
    renderWithQueryClient();
    // The component retries once (retry: 1) before settling into the error
    // state, so give this more room than the default waitFor timeout.
    await waitFor(() => expect(screen.getByText(/couldn't load candidates/i)).toBeInTheDocument(), { timeout: 5000 });
    expect(screen.getByText(/server responded 500/i)).toBeInTheDocument();
    // The old bug: an error used to render exactly like a real empty organization.
    expect(screen.queryByText(/no candidates found/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/showing 0 of/i)).not.toBeInTheDocument();
  });

  it("shows all candidates and an honest count on success, including past the old 20-item cap", async () => {
    const candidates = Array.from({ length: 25 }, (_, i) => ({
      id: `c${i}`, fullName: `Candidate ${i}`, email: `c${i}@example.com`, status: "ACTIVE", role: "CANDIDATE",
    }));
    getMock.mockResolvedValue({ data: { content: candidates, totalElements: 25 } });
    renderWithQueryClient();
    await waitFor(() => expect(screen.getByText(/showing 25 of 25 candidates/i)).toBeInTheDocument());
    expect(screen.getByText("Candidate 24")).toBeInTheDocument();
  });

  it("shows a genuine empty state (distinct from the error state) when the org really has zero candidates", async () => {
    getMock.mockResolvedValue({ data: { content: [], totalElements: 0 } });
    renderWithQueryClient();
    await waitFor(() => expect(screen.getByText(/no candidates found/i)).toBeInTheDocument());
    expect(screen.getByText(/the request succeeded/i)).toBeInTheDocument();
    expect(screen.queryByText(/couldn't load candidates/i)).not.toBeInTheDocument();
  });
});
