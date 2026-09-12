import type { ChallengeView, TrainerProgressView } from '../models';
import {
  formatUiDate,
  formatUiNumber,
  msg,
} from '../i18n';
import { renderPresentationMessage } from '../presentationMessages';

const sections = ['METRICS', 'CHALLENGES', 'TIMELINE'] as const;

export function TrainerProgressPage({
  progress,
  onSelectSection,
}: {
  progress: TrainerProgressView;
  onSelectSection: (section: TrainerProgressView['selectedSection']) => void;
}) {
  const section = progress.selectedSection;
  const sectionLabels: Record<TrainerProgressView['selectedSection'], string> = {
    METRICS: msg('metrics'),
    CHALLENGES: msg('challenges'),
    TIMELINE: msg('timeline'),
  };

  return <div class="trainer-progress-content" data-scroll-region>
    <nav class="trainer-progress-tabs" aria-label={msg('progressSections')}>
      {sections.map(value => <button
        type="button"
        key={value}
        class={section === value ? 'active' : ''}
        aria-pressed={section === value}
        onClick={() => onSelectSection(value)}
      >{sectionLabels[value]}</button>)}
    </nav>
    {section === 'METRICS' && <Metrics progress={progress} />}
    {section === 'CHALLENGES' && <Challenges progress={progress} />}
    {section === 'TIMELINE' && <Timeline progress={progress} />}
  </div>;
}

function Metrics({ progress }: { progress: TrainerProgressView }) {
  return <div class="progress-metric-columns">
    <MetricSection title={msg('gameTotals')} metrics={progress.gameTotals} />
    <MetricSection title={msg('trackedJourney')} metrics={progress.trackedJourney} />
  </div>;
}

function MetricSection({ title, metrics }: { title: string; metrics: TrainerProgressView['gameTotals'] }) {
  return <section class="progress-panel">
    <h2>{title}</h2>
    <dl class="progress-metric-grid">
      {metrics.map(metric => <div key={metric.key}><dt>{renderPresentationMessage(metric.label)}</dt><dd>{formatMetric(metric.key, metric.value)}</dd></div>)}
    </dl>
  </section>;
}

function Challenges({ progress }: { progress: TrainerProgressView }) {
  if (progress.challenges.length === 0) {
    return <ProgressEmpty title={msg('noChallengesYet')} detail={msg('objectivesAppear')} />;
  }

  const categories = [...new Set(progress.challenges.map(challenge => challenge.category))];
  const summary = progress.challengeSummary;
  const summaryPercent = summary.completionPercent == null
    ? null
    : formatUiNumber(summary.completionPercent);

  return <div class="challenge-groups">
    {summaryPercent != null && <section class="challenge-summary" aria-label={msg('overallChallengeProgress', summaryPercent)}>
      <strong>{summaryPercent}%</strong>
      <span>{msg('overallProgress')}</span>
      <small>{msg('completedCount', formatUiNumber(summary.completed), formatUiNumber(summary.applicable))}</small>
    </section>}
    {categories.map(category => <section key={category} class="progress-panel challenge-group">
      <h2>{categoryLabel(category)}</h2>
      <div class="challenge-list">{progress.challenges.filter(challenge => challenge.category === category).map(challenge => {
        const title = renderPresentationMessage(challenge.title);
        const percent = challenge.completionPercent == null
          ? null
          : formatUiNumber(challenge.completionPercent);
        return <article key={challenge.key} class={`challenge-card ${challenge.complete ? 'is-complete' : ''}`}>
          <div><strong>{title}</strong>{challenge.complete && <span>{msg('complete')}</span>}</div>
          <p>{renderPresentationMessage(challenge.description)}</p>
          {challenge.target != null && challenge.completionPercent != null && percent != null && <div
            class="challenge-progress"
            role="progressbar"
            aria-label={msg('challengeProgress', title, percent)}
            aria-valuemin={0}
            aria-valuemax={100}
            aria-valuenow={challenge.completionPercent}
          ><i style={{ width: `${challenge.completionPercent}%` }} /><b>{formatUiNumber(challenge.progress ?? 0)} / {formatUiNumber(challenge.target)} · {percent}%</b></div>}
        </article>;
      })}</div>
    </section>)}
  </div>;
}

function Timeline({ progress }: { progress: TrainerProgressView }) {
  if (progress.timeline.length === 0) {
    return <ProgressEmpty title={msg('noSavedMomentsYet')} detail={msg('changesAppearAfterSave')} />;
  }

  return <section class="progress-panel timeline-panel">
    <h2>{msg('saveTimeline')}</h2>
    <ol>{progress.timeline.map((entry, index) => <li key={`${entry.recordedAtEpochMs}-${index}`} class={entry.milestone ? 'is-milestone' : ''}>
      <time dateTime={new Date(entry.recordedAtEpochMs).toISOString()}>{formatUiDate(entry.recordedAtEpochMs, { dateStyle: 'short', timeStyle: 'short' })}</time>
      <div>{entry.changes.map((change, changeIndex) => {
        const rendered = renderPresentationMessage(change);
        return <span key={`${change.code}-${changeIndex}`}>{rendered}</span>;
      })}</div>
    </li>)}</ol>
  </section>;
}

function ProgressEmpty({ title, detail }: { title: string; detail: string }) {
  return <div class="progress-empty"><strong>{title}</strong><p>{detail}</p></div>;
}

function formatMetric(key: string, value: number | null): string {
  if (value == null) return '—';
  if (key === 'play-time') {
    return `${formatUiNumber(Math.floor(value / 60), { useGrouping: false })}:${formatUiNumber(value % 60, { minimumIntegerDigits: 2, useGrouping: false })}`;
  }
  return formatUiNumber(value);
}

function categoryLabel(category: ChallengeView['category']): string {
  const labels: Record<ChallengeView['category'], string> = {
    PROGRESS: msg('categoryProgress'),
    COLLECTION: msg('categoryCollection'),
    EXPLORATION: msg('categoryExploration'),
    BATTLE: msg('categoryBattle'),
    PARTY: msg('categoryParty'),
    SPECIAL: msg('categorySpecial'),
  };
  return labels[category];
}
