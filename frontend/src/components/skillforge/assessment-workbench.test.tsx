import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
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

import { AssessmentWorkbench } from "./assessment-workbench";

function renderWithQueryClient() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <AssessmentWorkbench />
    </QueryClientProvider>,
  );
}

describe("AssessmentWorkbench publish flow", () => {
  beforeEach(() => {
    getMock.mockReset();
    postMock.mockReset();
    // Assessments list + candidates list queries fired on mount.
    getMock.mockResolvedValue({ data: { content: [] } });
  });

  it("creates a section and attaches real approved questions before publishing (not zero, like before)", async () => {
    postMock.mockImplementation((url: string) => {
      if (url === "/assessments") return Promise.resolve({ data: { id: "assessment-1" } });
      if (url === "/assessments/assessment-1/sections") return Promise.resolve({ data: { id: "section-1" } });
      if (url === "/assessments/assessment-1/questions") return Promise.resolve({ data: {} });
      if (url === "/assessments/assessment-1/publish") return Promise.resolve({ data: {} });
      return Promise.reject(new Error(`Unexpected POST ${url}`));
    });
    // Catalog GET returns 2 approved EASY questions, matching draft.easy's
    // default of 10 is irrelevant here -- size is capped to whatever the
    // catalog actually has.
    getMock.mockImplementation((url: string) => {
      if (url === "/catalog/questions") {
        return Promise.resolve({ data: [{ id: "q1" }, { id: "q2" }] });
      }
      return Promise.resolve({ data: { content: [] } });
    });

    renderWithQueryClient();

    fireEvent.click(screen.getByRole("button", { name: /publish/i }));

    await waitFor(() => expect(postMock).toHaveBeenCalledWith("/assessments/assessment-1/publish"));

    // The critical regression check: sections and questions must be
    // attached BEFORE publish is called, not skipped entirely.
    expect(postMock).toHaveBeenCalledWith("/assessments/assessment-1/sections", expect.objectContaining({ name: expect.any(String) }));
    expect(postMock).toHaveBeenCalledWith("/assessments/assessment-1/questions", { questionId: "q1" });
    expect(postMock).toHaveBeenCalledWith("/assessments/assessment-1/questions", { questionId: "q2" });

    await waitFor(() => expect(screen.getByText(/real approved/i)).toBeInTheDocument());
  });

  it("shows the backend's real send summary, not a count derived from candidates selected", async () => {
    getMock.mockImplementation((url: string) => {
      if (url === "/candidates") {
        return Promise.resolve({ data: { content: [{ id: "cand-1" }, { id: "cand-2" }] } });
      }
      if (url === "/catalog/questions") {
        return Promise.resolve({ data: [{ id: "q1" }, { id: "q2" }] });
      }
      return Promise.resolve({ data: { content: [] } });
    });
    postMock.mockImplementation((url: string) => {
      if (url === "/assessments") return Promise.resolve({ data: { id: "assessment-1" } });
      if (url === "/assessments/assessment-1/sections") return Promise.resolve({ data: { id: "section-1" } });
      if (url === "/assessments/assessment-1/questions") return Promise.resolve({ data: {} });
      if (url === "/assessments/assessment-1/publish") return Promise.resolve({ data: {} });
      if (url === "/assessments/assessment-1/invite") {
        // Two candidates selected, but SMTP isn't configured -- 0 actually sent.
        return Promise.resolve({
          data: {
            recipientsSelected: 2, invitationsCreated: 2, emailsAttempted: 2,
            emailsSent: 0, emailsFailed: 0, smtpConfigured: false,
            summary: "2 invitation(s) created, but 0 emails sent because SMTP is not configured on this server.",
            invitations: [{ id: "inv-1", candidateUserId: "cand-1", tokenPreview: "abc123..." }],
          },
        });
      }
      return Promise.reject(new Error(`Unexpected POST ${url}`));
    });

    renderWithQueryClient();
    fireEvent.click(screen.getByRole("button", { name: /publish/i }));
    await waitFor(() => expect(postMock).toHaveBeenCalledWith("/assessments/assessment-1/publish"));

    fireEvent.click(screen.getByRole("button", { name: /send invitations/i }));
    await waitFor(() => expect(screen.getByText(/0 emails sent because smtp is not configured/i)).toBeInTheDocument());
    // The old bug: this used to say "Sent 2 invitation(s) by email" regardless of SMTP outcome.
    expect(screen.queryByText(/^sent 2 invitation/i)).not.toBeInTheDocument();
  });

  it("refuses to publish and shows a clear message when no approved questions exist for the subject", async () => {
    postMock.mockImplementation((url: string) => {
      if (url === "/assessments") return Promise.resolve({ data: { id: "assessment-1" } });
      if (url === "/assessments/assessment-1/sections") return Promise.resolve({ data: { id: "section-1" } });
      return Promise.reject(new Error(`Unexpected POST ${url}`));
    });
    getMock.mockImplementation((url: string) => {
      if (url === "/catalog/questions") return Promise.resolve({ data: [] }); // nothing approved yet
      return Promise.resolve({ data: { content: [] } });
    });

    renderWithQueryClient();
    fireEvent.click(screen.getByRole("button", { name: /publish/i }));

    await waitFor(() =>
      expect(screen.getByText(/no approved .* questions are available/i)).toBeInTheDocument(),
    );

    // Must never call publish if nothing was actually attached.
    expect(postMock).not.toHaveBeenCalledWith("/assessments/assessment-1/publish");
  });

  it("surfaces the backend's real error message on a failed publish, not a generic one", async () => {
    postMock.mockImplementation((url: string) => {
      if (url === "/assessments") return Promise.resolve({ data: { id: "assessment-1" } });
      if (url === "/assessments/assessment-1/sections") return Promise.resolve({ data: { id: "section-1" } });
      if (url === "/assessments/assessment-1/questions") return Promise.resolve({ data: {} });
      if (url === "/assessments/assessment-1/publish") {
        return Promise.reject({ response: { data: { error: "Assessment requires at least one approved question before publishing" } } });
      }
      return Promise.reject(new Error(`Unexpected POST ${url}`));
    });
    getMock.mockImplementation((url: string) => {
      if (url === "/catalog/questions") return Promise.resolve({ data: [{ id: "q1" }] });
      return Promise.resolve({ data: { content: [] } });
    });

    renderWithQueryClient();
    fireEvent.click(screen.getByRole("button", { name: /publish/i }));

    await waitFor(() =>
      expect(screen.getByText("Assessment requires at least one approved question before publishing")).toBeInTheDocument(),
    );
  });
});
