import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch, ApiError } from "../api/client";
import { useToast } from "./Toast";
import IconPicker from "./IconPicker";
import { getIcon } from "../lib/icons";

interface TypeRow {
  id: number;
  name: string;
  icon?: string | null;
  color: string | null;
  archived: number;
  usage_count: number;
}

interface Props {
  basePath: string;
  hasIcon: boolean;
  queryKey: string;
  noun: string;
}

/** reminder_types and phase_types are the same shape on the server (spec §5),
 * so one management UI, parameterized by basePath/hasIcon, serves both. */
export default function TypeManager({ basePath, hasIcon, queryKey, noun }: Props) {
  const queryClient = useQueryClient();
  const { showToast } = useToast();
  const [showArchived, setShowArchived] = useState(false);
  const [newName, setNewName] = useState("");
  const [newIcon, setNewIcon] = useState<string | null>(null);
  const [newColor, setNewColor] = useState("#cba6f7");

  const listQuery = useQuery({
    queryKey: [queryKey, { archived: showArchived }],
    queryFn: () => apiFetch<TypeRow[]>(`${basePath}?archived=${showArchived}`),
  });

  function invalidate() {
    queryClient.invalidateQueries({ queryKey: [queryKey] });
  }

  const create = useMutation({
    mutationFn: () =>
      apiFetch(basePath, {
        method: "POST",
        body: { name: newName, icon: hasIcon ? newIcon ?? undefined : undefined, color: newColor || undefined },
      }),
    onSuccess: () => {
      setNewName("");
      setNewIcon(null);
      invalidate();
      showToast(`${noun} created`, "success");
    },
    onError: (err) => showToast((err as ApiError).message ?? `Failed to create ${noun.toLowerCase()}`, "error"),
  });

  const archive = useMutation({
    mutationFn: ({ id, action }: { id: number; action: "archive" | "unarchive" }) =>
      apiFetch(`${basePath}/${id}/${action}`, { method: "POST" }),
    onSuccess: (_data, variables) => {
      invalidate();
      showToast(`${noun} ${variables.action === "archive" ? "archived" : "unarchived"}`, "success");
    },
    onError: (err) => showToast((err as ApiError).message ?? "Something went wrong", "error"),
  });

  return (
    <div>
      <div className="card" style={{ marginBottom: "1.25rem" }}>
        <h2 style={{ marginTop: 0 }}>Add {noun.toLowerCase()}</h2>
        <div style={{ display: "flex", gap: "0.75rem", flexWrap: "wrap", alignItems: "flex-end" }}>
          <div className="form-row" style={{ marginBottom: 0 }}>
            <label>Name</label>
            <input value={newName} onChange={(e) => setNewName(e.target.value)} />
          </div>
          {hasIcon && (
            <div className="form-row" style={{ marginBottom: 0 }}>
              <label>Icon</label>
              <IconPicker value={newIcon} onChange={setNewIcon} />
            </div>
          )}
          <div className="form-row" style={{ marginBottom: 0 }}>
            <label>Color</label>
            <input type="color" value={newColor} onChange={(e) => setNewColor(e.target.value)} />
          </div>
          <button type="button" className="btn" onClick={() => create.mutate()} disabled={!newName.trim() || create.isPending}>
            Add
          </button>
        </div>
        {create.isError && <p style={{ color: "var(--overdue)" }}>{(create.error as ApiError).message}</p>}
      </div>

      <label style={{ display: "flex", gap: "0.4rem", alignItems: "center", marginBottom: "0.85rem" }}>
        <input type="checkbox" checked={showArchived} onChange={(e) => setShowArchived(e.target.checked)} />
        Show archived
      </label>

      <div>
        {(listQuery.data ?? []).map((row) => {
          const Icon = getIcon(row.icon);
          return (
            <div key={row.id} className="item-row">
              <div className="item-row-main">
                <span
                  style={{
                    display: "inline-flex",
                    width: "2.2rem",
                    height: "2.2rem",
                    borderRadius: "999px",
                    background: row.color ? `${row.color}29` : "var(--accent-soft)",
                    color: row.color ?? "var(--accent)",
                    alignItems: "center",
                    justifyContent: "center",
                    flexShrink: 0,
                  }}
                >
                  {Icon ? <Icon size={18} /> : <span style={{ width: "0.6rem", height: "0.6rem", borderRadius: "999px", background: row.color ?? "var(--accent)" }} />}
                </span>
                <div>
                  <div style={{ fontWeight: 600 }}>{row.name}</div>
                  <div style={{ fontSize: "0.78rem", color: "var(--text-muted)" }}>Used by {row.usage_count} plant{row.usage_count === 1 ? "" : "s"}</div>
                </div>
              </div>
              <div className="item-row-actions">
                <button
                  type="button"
                  className="btn secondary"
                  onClick={() => archive.mutate({ id: row.id, action: row.archived ? "unarchive" : "archive" })}
                >
                  {row.archived ? "Unarchive" : "Archive"}
                </button>
              </div>
            </div>
          );
        })}
        {listQuery.data && listQuery.data.length === 0 && <p style={{ color: "var(--text-muted)" }}>Nothing here yet.</p>}
      </div>
    </div>
  );
}
