import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch, ApiError } from "../api/client";

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
}

/** reminder_types and phase_types are the same shape on the server (spec §5),
 * so one management UI, parameterized by basePath/hasIcon, serves both. */
export default function TypeManager({ basePath, hasIcon, queryKey }: Props) {
  const queryClient = useQueryClient();
  const [showArchived, setShowArchived] = useState(false);
  const [newName, setNewName] = useState("");
  const [newIcon, setNewIcon] = useState("");
  const [newColor, setNewColor] = useState("#89b4fa");

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
        body: { name: newName, icon: hasIcon ? newIcon || undefined : undefined, color: newColor || undefined },
      }),
    onSuccess: () => {
      setNewName("");
      setNewIcon("");
      invalidate();
    },
  });

  const archive = useMutation({
    mutationFn: ({ id, action }: { id: number; action: "archive" | "unarchive" }) =>
      apiFetch(`${basePath}/${id}/${action}`, { method: "POST" }),
    onSuccess: invalidate,
  });

  return (
    <div>
      <div className="card" style={{ marginBottom: "1rem" }}>
        <h2 style={{ marginTop: 0 }}>Add new</h2>
        <div style={{ display: "flex", gap: "0.5rem", flexWrap: "wrap", alignItems: "flex-end" }}>
          <div className="form-row" style={{ marginBottom: 0 }}>
            <label>Name</label>
            <input value={newName} onChange={(e) => setNewName(e.target.value)} />
          </div>
          {hasIcon && (
            <div className="form-row" style={{ marginBottom: 0 }}>
              <label>Icon</label>
              <input value={newIcon} onChange={(e) => setNewIcon(e.target.value)} placeholder="droplet" />
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

      <label style={{ display: "flex", gap: "0.4rem", alignItems: "center", marginBottom: "0.75rem" }}>
        <input type="checkbox" checked={showArchived} onChange={(e) => setShowArchived(e.target.checked)} />
        Show archived
      </label>

      <table>
        <thead>
          <tr>
            <th>Name</th>
            <th>Color</th>
            <th>Usage</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {(listQuery.data ?? []).map((row) => (
            <tr key={row.id}>
              <td>{row.name}</td>
              <td>
                {row.color && (
                  <span
                    style={{
                      display: "inline-block",
                      width: "1rem",
                      height: "1rem",
                      borderRadius: "999px",
                      background: row.color,
                      verticalAlign: "middle",
                    }}
                  />
                )}
              </td>
              <td>{row.usage_count}</td>
              <td>
                <button
                  type="button"
                  className="btn secondary"
                  onClick={() => archive.mutate({ id: row.id, action: row.archived ? "unarchive" : "archive" })}
                >
                  {row.archived ? "Unarchive" : "Archive"}
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
