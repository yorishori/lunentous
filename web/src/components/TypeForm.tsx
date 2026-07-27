import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { apiFetch, ApiError } from "../api/client";
import { useToast } from "./Toast";
import IconPicker from "./IconPicker";
import ColorPicker from "./ColorPicker";
import Spinner from "./Spinner";

interface TypeRow {
  id: number;
  name: string;
  icon?: string | null;
  color: string | null;
}

interface Props {
  basePath: string;
  hasIcon: boolean;
  queryKey: string;
  noun: string;
  existing?: TypeRow;
  onDone: () => void;
}

/** Shared create/edit form for reminder_types and phase_types (spec §5: "same
 * shape as Reminder Types" aside from icon) -- used both inline (add) and
 * inside a slide-over (edit), see TypeManager. */
export default function TypeForm({ basePath, hasIcon, queryKey, noun, existing, onDone }: Props) {
  const queryClient = useQueryClient();
  const { showToast } = useToast();
  const [name, setName] = useState(existing?.name ?? "");
  const [icon, setIcon] = useState<string | null>(existing?.icon ?? null);
  const [color, setColor] = useState(existing?.color ?? "#cba6f7");

  const save = useMutation({
    mutationFn: () => {
      const body = { name, icon: hasIcon ? (icon ?? undefined) : undefined, color: color || undefined };
      return existing
        ? apiFetch(`${basePath}/${existing.id}`, { method: "PATCH", body })
        : apiFetch(basePath, { method: "POST", body });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [queryKey] });
      showToast(existing ? `${noun} updated` : `${noun} created`, "success");
      onDone();
    },
    onError: (err) => showToast((err as ApiError).message ?? `Failed to save ${noun.toLowerCase()}`, "error"),
  });

  return (
    <div>
      <div className="form-row">
        <label>Name</label>
        <input value={name} onChange={(e) => setName(e.target.value)} autoFocus />
      </div>
      {hasIcon && (
        <div className="form-row">
          <label>Icon</label>
          <IconPicker value={icon} onChange={setIcon} />
        </div>
      )}
      <div className="form-row">
        <label>Color</label>
        <ColorPicker value={color} onChange={setColor} />
      </div>
      {save.isError && <p style={{ color: "var(--overdue)" }}>{(save.error as ApiError).message}</p>}
      <button
        type="button"
        className="btn"
        onClick={() => save.mutate()}
        disabled={!name.trim() || save.isPending}
        style={{ width: "100%" }}
      >
        {save.isPending && <Spinner size={14} />} {existing ? "Save changes" : `Add ${noun.toLowerCase()}`}
      </button>
    </div>
  );
}
