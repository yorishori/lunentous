import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { setToken } from "../api/client";

export default function Login() {
  const [value, setValue] = useState("");
  const navigate = useNavigate();

  function handleSubmit(e: FormEvent) {
    e.preventDefault();
    const trimmed = value.trim();
    if (!trimmed) return;
    setToken(trimmed);
    navigate("/", { replace: true });
  }

  return (
    <div className="login-page">
      <form onSubmit={handleSubmit} className="login-form">
        <h1>Lunentous</h1>
        <p>Enter your API key to continue. Create one with the server's
          {" "}<code>npm run cli:create-api-key</code> command.</p>
        <input
          type="password"
          value={value}
          onChange={(e) => setValue(e.target.value)}
          placeholder="API key"
          autoFocus
        />
        <button type="submit" disabled={!value.trim()}>
          Continue
        </button>
      </form>
    </div>
  );
}
