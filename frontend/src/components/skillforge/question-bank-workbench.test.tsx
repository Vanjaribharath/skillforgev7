import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";

vi.mock("@/store/use-organization-store", () => ({
  useOrganizationStore: (selector: (s: { organizationId: string }) => unknown) => selector({ organizationId: "org-1" }),
}));
vi.mock("@/store/use-auth-store", () => ({
  useAuthStore: (selector: (s: { user: { id: string } } ) => unknown) => selector({ user: { id: "user-1" } }),
}));

const getMock = vi.fn();
const postMock = vi.fn();
vi.mock("@/lib/api-client", () => ({
  api: {
    get: (...args: unknown[]) => getMock(...args),
    post: (...args: unknown[]) => postMock(...args),
  },
}));

import { QuestionBankWorkbench } from "./question-bank-workbench";

function csvFile(contents: string) {
  return new File([contents], "questions.csv", { type: "text/csv" });
}

describe("QuestionBankWorkbench CSV import", () => {
  beforeEach(() => {
    getMock.mockReset();
    postMock.mockReset();
    getMock.mockResolvedValue({ data: [] }); // coverage + catalog queries on mount
  });

  it("imports a CSV as raw text/plain with organizationId + createdBy as query params, and shows the result summary", async () => {
    postMock.mockResolvedValue({
      data: { totalRecords: 3, importedSuccessfully: 2, duplicates: 1, invalidQuestions: 0, failedRows: 0, warnings: [] },
    });

    const { container } = render(<QuestionBankWorkbench />);
    const fileInput = container.querySelector('input[type="file"]') as HTMLInputElement;
    const csvContents = "subject,prompt,type,difficulty,options,correct_answer\nJava,What is a JVM?,MULTIPLE_CHOICE,EASY,A|B|C,A";

    fireEvent.change(fileInput, { target: { files: [csvFile(csvContents)] } });

    await waitFor(() =>
      expect(postMock).toHaveBeenCalledWith(
        "/questions/import/csv",
        csvContents,
        expect.objectContaining({
          params: { organizationId: "org-1", createdBy: "user-1" },
          headers: { "Content-Type": "text/plain" },
        }),
      ),
    );

    // The import summary counts are what persist on screen (the transient
    // "Import complete..." status message gets overwritten immediately
    // afterward by the coverage/questions reload that follows a successful
    // import) -- checking container text directly sidesteps ambiguity from
    // the count numbers being split across nested <span> elements.
    await waitFor(() => expect(container.textContent).toContain("imported"));
    expect(container.textContent).toContain("2 imported");
    expect(container.textContent).toContain("1 duplicates");
  });

  it("surfaces the backend's real error message on a failed import instead of a generic one", async () => {
    postMock.mockRejectedValue({ response: { data: { error: "CSV is too large (max 2MB) — split it into smaller files" } } });

    const { container } = render(<QuestionBankWorkbench />);
    const fileInput = container.querySelector('input[type="file"]') as HTMLInputElement;
    fireEvent.change(fileInput, { target: { files: [csvFile("subject,prompt\nJava,x")] } });

    await waitFor(() => expect(screen.getByText(/CSV is too large/i)).toBeInTheDocument());
  });

  it("never calls the import endpoint if organization/user context is missing", async () => {
    // Import a fresh instance of the module with an auth mock returning no user.
    vi.resetModules();
    vi.doMock("@/store/use-organization-store", () => ({
      useOrganizationStore: (selector: (s: { organizationId: string | null }) => unknown) => selector({ organizationId: null }),
    }));
    vi.doMock("@/store/use-auth-store", () => ({
      useAuthStore: (selector: (s: { user: null }) => unknown) => selector({ user: null }),
    }));
    vi.doMock("@/lib/api-client", () => ({ api: { get: getMock, post: postMock } }));
    const { QuestionBankWorkbench: WorkbenchWithNoSession } = await import("./question-bank-workbench");

    const { container } = render(<WorkbenchWithNoSession />);
    const fileInput = container.querySelector('input[type="file"]') as HTMLInputElement;
    fireEvent.change(fileInput, { target: { files: [csvFile("subject,prompt\nJava,x")] } });

    await waitFor(() => expect(screen.getByText(/Sign in again/i)).toBeInTheDocument());
    expect(postMock).not.toHaveBeenCalledWith("/questions/import/csv", expect.anything(), expect.anything());
  });
});
