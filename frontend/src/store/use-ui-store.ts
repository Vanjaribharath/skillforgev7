import { create } from "zustand";

type UIState = {
  commandOpen: boolean;
  setCommandOpen: (open: boolean) => void;
};

export const useUIStore = create<UIState>((set) => ({
  commandOpen: false,
  setCommandOpen: (commandOpen) => set({ commandOpen }),
}));
