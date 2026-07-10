import { create } from "zustand";
import { useAuthStore } from "@/store/use-auth-store";

type OrganizationState = {
  organizationId: string;
  setOrganizationId: (id: string) => void;
};

// Previously a hardcoded placeholder UUID. Now derives from the logged-in
// user's real organizationId (set by useAuthStore on login) — setOrganizationId
// remains available for cross-org admin tooling, should that be needed later.
export const useOrganizationStore = create<OrganizationState>((set) => ({
  organizationId: "",
  setOrganizationId: (id) => set({ organizationId: id }),
}));

// Keeps the organization store in sync with whichever user is currently
// authenticated, without every consumer needing to duplicate this wiring.
useAuthStore.subscribe((state) => {
  useOrganizationStore.setState({ organizationId: state.user?.organizationId ?? "" });
});
