import { Link } from "react-router-dom";

export default function NotFound() {
  return (
    <section className="hero">
      <h1>Page not found</h1>
      <p>The route you requested is not available yet.</p>
      <div className="actions">
        <Link className="cta" to="/">
          Return home
        </Link>
      </div>
    </section>
  );
}
