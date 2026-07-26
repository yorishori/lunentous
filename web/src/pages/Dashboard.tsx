import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "../api/client";
import type { Plant } from "../api/types";
import PlantCard from "../components/PlantCard";

export default function Dashboard() {
  const { data: plants, isLoading, error } = useQuery({
    queryKey: ["plants", { archived: false }],
    queryFn: () => apiFetch<Plant[]>("/plants?archived=false"),
  });

  if (isLoading) return <p>Loading plants…</p>;
  if (error) return <p>Failed to load plants.</p>;

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "1rem" }}>
        <h1 style={{ margin: 0 }}>Dashboard</h1>
        <Link to="/plants/new" className="btn">
          Add plant
        </Link>
      </div>
      {plants && plants.length === 0 && <p>No plants yet. Add your first one.</p>}
      <div className="card-grid">
        {plants?.map((plant) => (
          <PlantCard key={plant.id} plant={plant} />
        ))}
      </div>
    </div>
  );
}
