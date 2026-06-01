import { Link } from "react-router-dom";

export default function NotFound() {
  return (
    <main>
      <h1>Page not found</h1>
      <p>The route you requested does not exist.</p>
      <p>
        <Link to="/">Go back home</Link>
      </p>
    </main>
  );
}
