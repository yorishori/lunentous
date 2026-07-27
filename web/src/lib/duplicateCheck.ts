import { apiFetch } from "../api/client";
import type { TimelineEvent } from "../api/types";

/** True if this plant already has a timeline entry of the same reminder
 * type on the same day -- used to warn before creating what's likely an
 * accidental double-log. */
export async function hasDuplicateEntry(plantId: number, reminderTypeId: number, date: string): Promise<boolean> {
  const existing = await apiFetch<TimelineEvent[]>(
    `/plants/${plantId}/timeline?reminder_type_id=${reminderTypeId}&from=${date}&to=${date}&limit=1`
  );
  return existing.length > 0;
}
