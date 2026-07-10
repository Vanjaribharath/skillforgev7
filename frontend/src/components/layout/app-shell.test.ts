import { describe, expect, it } from "vitest";
import { allowedRolesFor, landingPageFor, nav } from "./app-shell";

describe("allowedRolesFor", () => {
  it("restricts /admin and /settings to admin roles only", () => {
    expect(allowedRolesFor("/admin")).toEqual(["PLATFORM_ADMIN", "ORG_ADMIN"]);
    expect(allowedRolesFor("/settings")).toEqual(["PLATFORM_ADMIN", "ORG_ADMIN"]);
  });

  it("does not include CANDIDATE in any staff-only route", () => {
    for (const item of nav) {
      if (item.href === "/candidate") continue;
      expect(item.roles).not.toContain("CANDIDATE");
    }
  });

  it("allows every staff role on Command", () => {
    expect(allowedRolesFor("/")).toEqual(["PLATFORM_ADMIN", "ORG_ADMIN", "TRAINER", "EVALUATOR"]);
  });

  it("returns null for unknown routes (no restriction applied)", () => {
    expect(allowedRolesFor("/some/unlisted/route")).toBeNull();
  });
});

describe("landingPageFor", () => {
  it("sends candidates to the test player, not the staff dashboard", () => {
    expect(landingPageFor("CANDIDATE")).toBe("/candidate");
  });

  it("sends every staff role to Command", () => {
    expect(landingPageFor("ORG_ADMIN")).toBe("/");
    expect(landingPageFor("TRAINER")).toBe("/");
    expect(landingPageFor(undefined)).toBe("/");
  });
});
