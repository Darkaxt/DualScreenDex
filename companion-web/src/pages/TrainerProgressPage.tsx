import type { TrainerProgressView } from '../models';

export function TrainerProgressPage({
  progress,
  onSelectSection,
}: {
  progress: TrainerProgressView;
  onSelectSection: (section: TrainerProgressView['selectedSection']) => void;
}) {
  const section = progress.selectedSection;
  return <div class="trainer-progress-content" data-scroll-region>
    <nav class="trainer-progress-tabs" aria-label="Progress sections">
      {(['METRICS', 'CHALLENGES', 'TIMELINE'] as const).map(value => <button
        type="button"
        key={value}
        class={section === value ? 'active' : ''}
        aria-pressed={section === value}
        onClick={() => onSelectSection(value)}
      >{titleCase(value)}</button>)}
    </nav>
    {section === 'METRICS' && <Metrics progress={progress} />}
    {section === 'CHALLENGES' && <Challenges progress={progress} />}
    {section === 'TIMELINE' && <Timeline progress={progress} />}
  </div>;
}

function Metrics({ progress }: { progress: TrainerProgressView }) {
  return <div class="progress-metric-columns">
    <MetricSection title="GAME TOTALS" metrics={progress.gameTotals} />
    <MetricSection title="TRACKED JOURNEY" metrics={progress.trackedJourney} />
  </div>;
}

function MetricSection({ title, metrics }: { title: string; metrics: TrainerProgressView['gameTotals'] }) {
  return <section class="progress-panel">
    <h2>{title}</h2>
    <dl class="progress-metric-grid">
      {metrics.map(metric => <div key={metric.key}><dt>{metric.label}</dt><dd>{formatMetric(metric.key, metric.value)}</dd></div>)}
    </dl>
  </section>;
}

function Challenges({ progress }: { progress: TrainerProgressView }) {
  if (progress.challenges.length === 0) return <ProgressEmpty title="NO CHALLENGES YET" detail="Objectives will appear as this game’s features become available." />;
  const categories = [...new Set(progress.challenges.map(challenge => challenge.category))];
  return <div class="challenge-groups">{categories.map(category => <section key={category} class="progress-panel challenge-group">
    <h2>{titleCase(category)}</h2>
    <div class="challenge-list">{progress.challenges.filter(challenge => challenge.category === category).map(challenge => <article key={challenge.key} class={`challenge-card ${challenge.complete ? 'is-complete' : ''}`}>
      <div><strong>{challenge.title}</strong>{challenge.complete && <span>COMPLETE</span>}</div>
      <p>{challenge.description}</p>
      {challenge.target != null && <div class="challenge-progress"><i style={{ width: `${Math.min(100, Math.max(0, ((challenge.progress ?? 0) / challenge.target) * 100))}%` }} /><b>{challenge.progress ?? 0} / {challenge.target}</b></div>}
    </article>)}</div>
  </section>)}</div>;
}

function Timeline({ progress }: { progress: TrainerProgressView }) {
  if (progress.timeline.length === 0) return <ProgressEmpty title="NO SAVED MOMENTS YET" detail="Changes will appear here after the game creates a new save." />;
  return <section class="progress-panel timeline-panel">
    <h2>SAVE TIMELINE</h2>
    <ol>{progress.timeline.map((entry, index) => <li key={`${entry.recordedAtEpochMs}-${index}`} class={entry.milestone ? 'is-milestone' : ''}>
      <time>{new Date(entry.recordedAtEpochMs).toLocaleString()}</time>
      <div>{entry.changes.map(change => <span key={change}>{change}</span>)}</div>
    </li>)}</ol>
  </section>;
}

function ProgressEmpty({ title, detail }: { title: string; detail: string }) {
  return <div class="progress-empty"><strong>{title}</strong><p>{detail}</p></div>;
}

function formatMetric(key: string, value: number | null) {
  if (value == null) return '—';
  if (key === 'play-time') return `${Math.floor(value / 60)}:${String(value % 60).padStart(2, '0')}`;
  if (key === 'money') return value.toLocaleString('en-US');
  return value.toLocaleString('en-US');
}

function titleCase(value: string) {
  return value.charAt(0) + value.slice(1).toLocaleLowerCase();
}
