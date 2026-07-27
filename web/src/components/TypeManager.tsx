import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Archive, ArchiveRestore, Pencil, Plus } from "lucide-react";
import { apiFetch, ApiError } from "../api/client";
import { useToast } from "./Toast";
import SlideOver from "./SlideOver";
import TypeForm from "./TypeForm";
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
  const [adding, setAdding] = useState(false);
  const [editing, setEditing] = useState<TypeRow | null>(null);

  const listQuery = useQuery({
    queryKey: [queryKey, { archived: showArchived }],
    queryFn: () => apiFetch<TypeRow[]>(`${basePath}?archived=${showArchived}`),
  });

  const archive = useMutation({
    mutationFn: ({ id, action }: { id: number; action: "archive" | "unarchive" }) =>
      apiFetch(`${basePath}/${id}/${action}`, { method: "POST" }),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: [queryKey] });
      showToast(`${noun} ${variables.action === "archive" ? "archived" : "unarchived"}`, "success");
    },
    onError: (err) => showToast((err as ApiError).message ?? "Something went wrong", "error"),
  });

  return (
    <div>
      <div className="section-header">
        <h2 style={{ margin: 0 }}>{noun}s</h2>
        <button type="button" className="btn" onClick={() => setAdding(true)}>
          <Plus size={15} /> Add {noun.toLowerCase()}
        </button>
      </div>

      <label className="checkbox-row" style={{ marginBottom: "0.85rem" }}>
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
                  {Icon ? (
                    <Icon size={18} />
                  ) : (
                    <span
                      style={{
                        width: "0.6rem",
                        height: "0.6rem",
                        borderRadius: "999px",
                        background: row.color ?? "var(--accent)",
                      }}
                    />
                  )}
                </span>
                <div>
                  <div style={{ fontWeight: 600 }}>{row.name}</div>
                  <div style={{ fontSize: "0.78rem", color: "var(--text-muted)" }}>
                    Used by {row.usage_count} plant{row.usage_count === 1 ? "" : "s"}
                  </div>
                </div>
              </div>
              <div className="item-row-actions">
                <button
                  type="button"
                  className="btn icon-btn secondary icon-btn-edit"
                  onClick={() => setEditing(row)}
                  aria-label={`Edit ${row.name}`}
                >
                  <Pencil size={14} />
                </button>
                <button
                  type="button"
                  className="btn icon-btn secondary icon-btn-archive"
                  onClick={() => archive.mutate({ id: row.id, action: row.archived ? "unarchive" : "archive" })}
                  aria-label={row.archived ? `Unarchive ${row.name}` : `Archive ${row.name}`}
                  title={row.archived ? "Unarchive" : "Archive"}
                >
                  {row.archived ? <ArchiveRestore size={14} /> : <Archive size={14} />}
                </button>
              </div>
            </div>
          );
        })}
        {listQuery.data && listQuery.data.length === 0 && <p style={{ color: "var(--text-muted)" }}>Nothing here yet.</p>}
      </div>

      <SlideOver open={adding} title={`Add ${noun.toLowerCase()}`} onClose={() => setAdding(false)}>
        <TypeForm basePath={basePath} hasIcon={hasIcon} queryKey={queryKey} noun={noun} onDone={() => setAdding(false)} />
      </SlideOver>

      <SlideOver open={editing !== null} title={`Edit ${noun.toLowerCase()}`} onClose={() => setEditing(null)}>
        {editing && (
          <TypeForm
            basePath={basePath}
            hasIcon={hasIcon}
            queryKey={queryKey}
            noun={noun}
            existing={editing}
            onDone={() => setEditing(null)}
          />
        )}
      </SlideOver>
    </div>
  );
}
