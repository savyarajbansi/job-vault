import { Link } from "react-router-dom";

export default function Home() {
  return (
    <div className="page">
      <section className="hero">
        <h1>JobVault Frontend</h1>
        <p>
          Scaffolded for routing, layouts, and API integrations. Use this space to
          wire core seeker, employer, and admin flows.
        </p>
        <div className="actions">
          <Link className="cta" to="/auth">
            Open auth console
          </Link>
          <Link className="cta secondary" to="/seeker">
            Open seeker workspace
          </Link>
          <a className="cta secondary" href="/api" rel="noreferrer">
            Backend health
          </a>
        </div>
      </section>

      <section className="grid">
        <div className="card">
          <h2>Seeker journeys</h2>
          <p>
            Resume upload, profile extraction, and ranked job matches land here.
            Align UI states with parsing + matching API responses.
          </p>
          <p>
            Start from the <Link to="/seeker">Seeker Workspace</Link> route to run
            upload, match, and skill-gap flows end-to-end.
          </p>
        </div>
        <div className="card">
          <h2>Employer pipeline</h2>
          <p>
            Create job postings, moderate status changes, and review applicant
            pipelines with skill gap details.
          </p>
        </div>
        <div className="card">
          <h2>Admin oversight</h2>
          <p>
            Track parsing and matching metrics without exposing any raw resume
            content. Keep error code visibility high.
          </p>
        </div>
      </section>
    </div>
  );
}
