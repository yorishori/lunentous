import type { ReactNode } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import { hasToken } from "./api/client";
import Nav from "./components/Nav";
import LoadingBar from "./components/LoadingBar";
import Login from "./pages/Login";
import Dashboard from "./pages/Dashboard";
import PlantDetail from "./pages/PlantDetail";
import Calendar from "./pages/Calendar";
import ReminderTypesSettings from "./pages/ReminderTypesSettings";
import PhaseTypesSettings from "./pages/PhaseTypesSettings";
import SettingsPage from "./pages/Settings";

function RequireAuth({ children }: { children: ReactNode }) {
  if (!hasToken()) return <Navigate to="/login" replace />;
  return <>{children}</>;
}

export default function App() {
  return (
    <>
      <LoadingBar />
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route
          path="/*"
          element={
            <RequireAuth>
              <div className="app-shell">
                <Nav />
                <main className="app-main">
                  <Routes>
                    <Route path="/" element={<Dashboard />} />
                    <Route path="/plants/new" element={<PlantDetail />} />
                    <Route path="/plants/:id" element={<PlantDetail />} />
                    <Route path="/calendar" element={<Calendar />} />
                    <Route path="/reminder-types" element={<ReminderTypesSettings />} />
                    <Route path="/phase-types" element={<PhaseTypesSettings />} />
                    <Route path="/settings" element={<SettingsPage />} />
                  </Routes>
                </main>
              </div>
            </RequireAuth>
          }
        />
      </Routes>
    </>
  );
}
