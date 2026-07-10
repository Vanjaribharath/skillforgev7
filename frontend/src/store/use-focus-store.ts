import { create } from "zustand";

type FocusState = {
  secondsRemaining: number;
  running: boolean;
  mode: "POMODORO" | "DEEP_WORK";
  start: (seconds: number) => void;
  pause: () => void;
  reset: () => void;
  tick: () => void;
};

export const useFocusStore = create<FocusState>((set) => ({
  secondsRemaining: 90 * 60,
  running: false,
  mode: "DEEP_WORK",
  start: (seconds) => set({ secondsRemaining: seconds, running: true }),
  pause: () => set({ running: false }),
  reset: () => set({ secondsRemaining: 90 * 60, running: false }),
  tick: () => set((state) => ({ secondsRemaining: Math.max(0, state.secondsRemaining - 1) })),
}));
