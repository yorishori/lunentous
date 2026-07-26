import { useState, type FormEvent } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { apiFetch, ApiError } from "../api/client";
import type { Plant, PlantDetail } from "../api/types";
import { useToast } from "./Toast";

interface Props {
  plant?: PlantDetail;
  onDone: (plant: Plant) => void;
}

export default function PlantForm({ plant, onDone }: Props) {
  const queryClient = useQueryClient();
  const { showToast } = useToast();
  const [name, setName] = useState(plant?.name ?? "");
  const [species, setSpecies] = useState(plant?.species ?? "");
  const [location, setLocation] = useState(plant?.location ?? "");
  const [acquiredDate, setAcquiredDate] = useState(plant?.acquired_date ?? "");
  const [generalNotes, setGeneralNotes] = useState(plant?.general_notes ?? "");
  const [avatarFile, setAvatarFile] = useState<File | null>(null);

  const save = useMutation({
    mutationFn: async () => {
      const body = {
        name,
        species: species || null,
        location: location || null,
        acquired_date: acquiredDate || null,
        general_notes: generalNotes || null,
      };
      const saved = plant
        ? await apiFetch<Plant>(`/plants/${plant.id}`, { method: "PATCH", body })
        : await apiFetch<Plant>("/plants", { method: "POST", body });

      if (avatarFile) {
        const formData = new FormData();
        formData.append("file", avatarFile);
        await apiFetch<Plant>(`/plants/${saved.id}/avatar`, { method: "POST", body: formData, isFormData: true });
      }
      return saved;
    },
    onSuccess: (saved) => {
      queryClient.invalidateQueries({ queryKey: ["plants"] });
      queryClient.invalidateQueries({ queryKey: ["plant", saved.id] });
      showToast(plant ? "Plant updated" : "Plant created", "success");
      onDone(saved);
    },
    onError: (err) => showToast((err as ApiError).message ?? "Failed to save plant", "error"),
  });

  function handleSubmit(e: FormEvent) {
    e.preventDefault();
    save.mutate();
  }

  return (
    <form onSubmit={handleSubmit}>
      <div className="form-row">
        <label>Name</label>
        <input value={name} onChange={(e) => setName(e.target.value)} required autoFocus />
      </div>
      <div className="form-row">
        <label>Species</label>
        <input value={species} onChange={(e) => setSpecies(e.target.value)} />
      </div>
      <div className="form-row">
        <label>Location</label>
        <input value={location} onChange={(e) => setLocation(e.target.value)} />
      </div>
      <div className="form-row">
        <label>Acquired date</label>
        <input type="date" value={acquiredDate} onChange={(e) => setAcquiredDate(e.target.value)} />
      </div>
      <div className="form-row">
        <label>General notes</label>
        <textarea value={generalNotes} onChange={(e) => setGeneralNotes(e.target.value)} rows={3} />
      </div>
      <div className="form-row">
        <label>Avatar photo</label>
        <input type="file" accept="image/*" onChange={(e) => setAvatarFile(e.target.files?.[0] ?? null)} />
      </div>
      {save.isError && <p style={{ color: "var(--overdue)" }}>{(save.error as ApiError).message}</p>}
      <button type="submit" className="btn" disabled={save.isPending} style={{ width: "100%" }}>
        {plant ? "Save changes" : "Create plant"}
      </button>
    </form>
  );
}
