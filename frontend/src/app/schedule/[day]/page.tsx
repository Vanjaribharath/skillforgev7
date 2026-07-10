import { Card } from "@/components/ui/card";

export default async function DayDetailPage({ params }: { params: Promise<{ day: string }> }) {
  const { day } = await params;
  return (
    <div className="space-y-5">
      <h1 className="text-3xl font-semibold">Day plan</h1>
      <Card>
        <p className="text-sm text-muted">Focused schedule for {day}.</p>
        <p className="mt-2 text-xs text-muted">No day-specific schedule breakdown exists on the backend yet — /schedules returns named recurring schedules, not a per-day view. See the main Schedule page for the real, wired list.</p>
      </Card>
    </div>
  );
}
