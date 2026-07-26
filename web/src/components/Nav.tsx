import { NavLink } from "react-router-dom";

export default function Nav() {
  return (
    <nav className="nav">
      <div className="nav-brand">Lunentous</div>
      <NavLink to="/" end>
        Dashboard
      </NavLink>
      <NavLink to="/calendar">Calendar</NavLink>
      <NavLink to="/reminder-types">Reminder Types</NavLink>
      <NavLink to="/phase-types">Phase Types</NavLink>
      <NavLink to="/settings">Settings</NavLink>
    </nav>
  );
}
