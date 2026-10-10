import { useEffect, useState } from 'react';
import { ApiError } from '../../shared/api';
import { isCancelled } from '../auth/authenticatedApi';
import { getStatistics, type LinkStatistics } from './statisticsApi';

type State = { status: 'loading' } | { status: 'error'; missing: boolean }
  | { status: 'ready'; data: LinkStatistics };
const dateLabel = (date: string) => new Intl.DateTimeFormat('en-GB', {
  day: 'numeric', month: 'short', timeZone: 'UTC',
}).format(new Date(`${date}T00:00:00Z`));

export function StatisticsPanel({ shortCode }: { shortCode: string }) {
  const [state, setState] = useState<State>({ status: 'loading' });
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setState({ status: 'loading' });
    getStatistics(shortCode, controller.signal).then(data => {
      if (!controller.signal.aborted) setState({ status: 'ready', data });
    }).catch((error: unknown) => {
      if (!controller.signal.aborted && !isCancelled(error)) {
        setState({ status: 'error', missing: error instanceof ApiError && error.problem.status === 404 });
      }
    });
    return () => controller.abort();
  }, [shortCode, attempt]);

  return <section id={`statistics-${shortCode}`} className="statistics-panel"
    aria-labelledby={`statistics-title-${shortCode}`} aria-busy={state.status === 'loading'}>
    <div className="statistics-heading">
      <h3 id={`statistics-title-${shortCode}`}>Click statistics</h3>
      <button type="button" disabled={state.status === 'loading'}
        onClick={() => setAttempt(value => value + 1)}>Refresh</button>
    </div>
    <p className="statistics-note">Counts may update with a delay. Clicks can be lost during service failures.
      Tracking includes GET requests, including bots and repeat visits; HEAD requests are excluded.</p>
    {state.status === 'loading' && <p role="status">Loading statistics…</p>}
    {state.status === 'error' && <div>
      <p className="error" role="alert">{state.missing ? 'This link is no longer available.' : 'Statistics could not be loaded. Please try again.'}</p>
      <button type="button" onClick={() => setAttempt(value => value + 1)}>Try again</button>
    </div>}
    {state.status === 'ready' && <>
      <div className="statistics-summary">
        <p>Total clicks <strong>{state.data.totalClicks.toLocaleString('en-GB')}</strong></p>
      </div>
      <p className="statistics-period">Last 30 days · UTC</p>
      {state.data.totalClicks === 0 && <p>No clicks recorded yet.</p>}
      <DailyChart data={state.data} />
      <details className="statistics-daily">
        <summary>Daily counts</summary>
        <table>
          <caption>Daily clicks in UTC, {dateLabel(state.data.from)}–{dateLabel(state.data.to)}</caption>
          <thead><tr><th scope="col">Date (UTC)</th><th scope="col">Clicks</th></tr></thead>
          <tbody>{state.data.daily.map(day => <tr key={day.date}>
            <th scope="row"><time dateTime={day.date}>{day.date}</time></th><td>{day.clicks.toLocaleString('en-GB')}</td>
          </tr>)}</tbody>
        </table>
      </details>
    </>}
  </section>;
}

function DailyChart({ data }: { data: LinkStatistics }) {
  const maximum = Math.max(...data.daily.map(day => day.clicks));
  return <figure className="statistics-chart">
    <p className="statistics-scale">Maximum: {maximum.toLocaleString('en-GB')} clicks per day</p>
    <svg viewBox="0 0 600 150" role="img" aria-label={`Daily clicks in UTC. Maximum ${maximum} clicks per day. Exact counts are available in Daily counts.`}>
      <line x1="0" y1="145" x2="600" y2="145" />
      {data.daily.map((day, index) => {
        const height = day.clicks / Math.max(1, maximum) * 125;
        return <rect key={day.date} x={2 + index * 20} y={145 - height} width="15" height={height}>
          <title>{`${day.date}: ${day.clicks} clicks`}</title>
        </rect>;
      })}
    </svg>
    <div className="statistics-dates" aria-hidden="true"><span>{dateLabel(data.from)}</span><span>{dateLabel(data.to)}</span></div>
    <figcaption>Daily clicks. Open Daily counts for exact values.</figcaption>
  </figure>;
}
