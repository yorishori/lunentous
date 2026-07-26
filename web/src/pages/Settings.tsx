import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch, ApiError, getToken } from "../api/client";
import type { ApiKey } from "../api/types";
import { useToast } from "../components/Toast";

export default function Settings() {
  const queryClient = useQueryClient();
  const { showToast } = useToast();
  const [label, setLabel] = useState("");
  const [createdToken, setCreatedToken] = useState<string | null>(null);

  const keysQuery = useQuery({
    queryKey: ["api-keys"],
    queryFn: () => apiFetch<ApiKey[]>("/api-keys"),
  });

  const create = useMutation({
    mutationFn: () =>
      apiFetch<{ id: number; label: string | null; token: string }>("/api-keys", {
        method: "POST",
        body: { label: label || null },
      }),
    onSuccess: (result) => {
      setCreatedToken(result.token);
      setLabel("");
      queryClient.invalidateQueries({ queryKey: ["api-keys"] });
      showToast("API key created", "success");
    },
    onError: (err) => showToast((err as ApiError).message ?? "Failed to create API key", "error"),
  });

  const revoke = useMutation({
    mutationFn: (id: number) => apiFetch(`/api-keys/${id}`, { method: "DELETE" }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["api-keys"] });
      showToast("API key revoked", "success");
    },
    onError: (err) => showToast((err as ApiError).message ?? "Failed to revoke API key", "error"),
  });

  async function downloadExport() {
    const token = getToken();
    const res = await fetch("/api/export", { headers: token ? { Authorization: `Bearer ${token}` } : {} });
    const blob = await res.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `lunentous-export-${new Date().toISOString().slice(0, 10)}.tar.gz`;
    a.click();
    URL.revokeObjectURL(url);
    showToast("Export downloaded", "success");
  }

  return (
    <div>
      <h1>Settings</h1>

      <section className="card" style={{ marginBottom: "1.5rem" }}>
        <h2 style={{ marginTop: 0 }}>API keys</h2>
        <div style={{ display: "flex", gap: "0.5rem", marginBottom: "1rem" }}>
          <input value={label} onChange={(e) => setLabel(e.target.value)} placeholder="Label (e.g. android-phone)" />
          <button type="button" className="btn" onClick={() => create.mutate()} disabled={create.isPending}>
            Create key
          </button>
        </div>
        {create.isError && <p style={{ color: "var(--overdue)" }}>{(create.error as ApiError).message}</p>}
        {createdToken && (
          <div className="card" style={{ marginBottom: "1rem", borderColor: "var(--accent)" }}>
            <p style={{ marginTop: 0 }}>New key created — copy it now, it won't be shown again:</p>
            <code style={{ wordBreak: "break-all" }}>{createdToken}</code>
          </div>
        )}
        <table>
          <thead>
            <tr>
              <th>Label</th>
              <th>Created</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {(keysQuery.data ?? []).map((key) => (
              <tr key={key.id}>
                <td>{key.label ?? "(unlabeled)"}</td>
                <td>{key.created_at}</td>
                <td>
                  <button type="button" className="btn secondary" onClick={() => revoke.mutate(key.id)}>
                    Revoke
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      <section className="card">
        <h2 style={{ marginTop: 0 }}>Backup</h2>
        <p>Download a full export of the database and photo library.</p>
        <button type="button" className="btn" onClick={downloadExport}>
          Download export
        </button>
      </section>
    </div>
  );
}
