import { useState } from "react";

import { AdminMetrics, getAdminMetrics } from "../api/admin";

export default function AdminMetricsPage() {
  const [metrics, setMetrics] = useState<AdminMetrics | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleLoadMetrics = async () => {
    setBusy(true);
    setError(null);
    try {
      const response = await getAdminMetrics();
      setMetrics(response);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Request failed.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <main>
      <h1>Admin metrics</h1>
      <p>Load backend metrics without any surrounding dashboard styling.</p>
      <p>
        <button onClick={() => void handleLoadMetrics()} disabled={busy}>
          {busy ? "Loading..." : "Load metrics"}
        </button>
      </p>

      {error && <p>{error}</p>}

      {metrics && (
        <>
          <section>
            <h2>Parsing</h2>
            <pre>Total attempts: {metrics.parse.totalAttempts}</pre>
            <pre>Success: {metrics.parse.successCount}</pre>
            <pre>Failures: {metrics.parse.failureCount}</pre>
            <pre>Latest: {metrics.parse.lastAttemptAt ?? "No data"}</pre>
            <pre>{JSON.stringify(metrics.parse.failuresByCode, null, 2)}</pre>
          </section>

          <section>
            <h2>Matching</h2>
            <pre>Total attempts: {metrics.match.totalAttempts}</pre>
            <pre>Success: {metrics.match.successCount}</pre>
            <pre>Failures: {metrics.match.failureCount}</pre>
            <pre>Latest: {metrics.match.lastAttemptAt ?? "No data"}</pre>
            <pre>{JSON.stringify(metrics.match.failuresByCode, null, 2)}</pre>
          </section>
        </>
      )}
    </main>
  );
}
