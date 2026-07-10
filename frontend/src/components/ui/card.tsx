import { clsx } from "clsx";

export function Card({ className, children }: { className?: string; children: React.ReactNode }) {
  return <section className={clsx("rounded-lg border border-line bg-white p-4 shadow-soft", className)}>{children}</section>;
}

export function Stat({ label, value, detail }: { label: string; value: string; detail: string }) {
  return (
    <Card>
      <div className="text-sm text-muted">{label}</div>
      <div className="mt-2 text-3xl font-semibold tracking-normal">{value}</div>
      <div className="mt-1 text-sm text-muted">{detail}</div>
    </Card>
  );
}
