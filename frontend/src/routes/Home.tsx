import { Link } from "react-router-dom";

export default function Home() {
  return (
    <main>
      <h1>Frontend reset</h1>
      <p>The existing UI has been stripped back to a blank scaffold.</p>
      <p>
        Use <Link to="/auth">Auth</Link>, <Link to="/seeker">Seeker</Link>, or{" "}
        <Link to="/admin/metrics">Admin</Link> as starting points for the rebuild.
      </p>
    </main>
  );
}
