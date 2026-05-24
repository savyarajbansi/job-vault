import { useState } from "react";

import { AdminMetrics, getAdminMetrics } from "../api/admin";

export default function AdminMetricsPage() {
  const [metrics, setMetrics] = useState<AdminMetrics | null>(null);
  const [metricsBusy, setMetricsBusy] = useState(false);
  const [metricsError, setMetricsError] = useState<string | null>(null);

  const handleLoadMetrics = async () => {
    setMetricsBusy(true);
    setMetricsError(null);
    try {
      const response = await getAdminMetrics();
      setMetrics(response);
    } catch (error) {
      const message = error instanceof Error ? error.message : "Request failed.";
      setMetricsError(message);
    } finally {
      setMetricsBusy(false);
    }
  };

  return (
    <div className="page">
      <section className="hero">
        <h1>Admin Metrics</h1>
        <p>Monitor parsing and matching attempts without exposing raw resume data.</p>
        <div className="actions">
          <button className="cta" onClick={handleLoadMetrics} disabled={metricsBusy}>
            {metricsBusy ? "Loading..." : "Load metrics"}
          </button>
        </div>
      </section>

      {metricsError && (
        <div className="status status-error" role="alert">
          {metricsError}
        </div>
      )}

      {metrics && (
        <section className="grid">
          <article className="card">
            <h2>Parsing</h2>
            <p className="mono">Total attempts: {metrics.parse.totalAttempts}</p>
            <p className="mono">Success: {metrics.parse.successCount}</p>
            <p className="mono">Failures: {metrics.parse.failureCount}</p>
            <p className="mono">Latest: {metrics.parse.lastAttemptAt ?? "No data"}</p>
            {Object.keys(metrics.parse.failuresByCode).length > 0 && (
              <>
                <h3>Failure codes</h3>
                <ul className="seeker-list">
                  {Object.entries(metrics.parse.failuresByCode).map(([code, count]) => (
                    <li key={code}>
                      <span className="mono">
                        {code}: {count}
                      </span>
                    </li>
                  ))}
                </ul>
              </>
            )}
          </article>

          <article className="card">
            <h2>Matching</h2>
            <p className="mono">Total attempts: {metrics.match.totalAttempts}</p>
            <p className="mono">Success: {metrics.match.successCount}</p>
            <p className="mono">Failures: {metrics.match.failureCount}</p>
            <p className="mono">Latest: {metrics.match.lastAttemptAt ?? "No data"}</p>
            {Object.keys(metrics.match.failuresByCode).length > 0 && (
              <>
                <h3>Failure codes</h3>
                <ul className="seeker-list">
                  {Object.entries(metrics.match.failuresByCode).map(([code, count]) => (
                    <li key={code}>
                      <span className="mono">
                        {code}: {count}
                      </span>
                    </li>
                  ))}
                </ul>
              </>
            )}
          </article>
        </section>
      )}
    </div>
  );
}
