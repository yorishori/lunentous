import { NavLink } from "react-router-dom";
import { Sprout, LayoutDashboard, CalendarDays, Bell, Leaf, Settings as SettingsIcon } from "lucide-react";

export default function Nav() {
  return (
    <header className="topbar">
      <div className="topbar-brand">
        <Sprout size={22} />
        Lunentous
      </div>
      <nav className="topbar-nav">
        <NavLink to="/" end>
          <LayoutDashboard size={16} /> Dashboard
        </NavLink>
        <NavLink to="/calendar">
          <CalendarDays size={16} /> Calendar
        </NavLink>
        <NavLink to="/reminder-types">
          <Bell size={16} /> Reminder Types
        </NavLink>
        <NavLink to="/phase-types">
          <Leaf size={16} /> Phase Types
        </NavLink>
        <NavLink to="/settings">
          <SettingsIcon size={16} /> Settings
        </NavLink>
      </nav>
    </header>
  );
}
