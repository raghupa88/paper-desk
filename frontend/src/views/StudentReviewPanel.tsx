import React, { useEffect, useState } from 'react';
import { useServices } from '../AppContext';
import { ModelIds } from '../core/events';
import { GradeInput } from '../core/types';
import { InstructorModel, InstructorState } from '../models/InstructorModel';
import { useModelState, fmtMoney, fmtNum, fmtSimTime, pnlCls } from './common';

const SCORE_LABELS: Record<number, string> = { 1: '1 — Needs work', 2: '2', 3: '3 — Solid', 4: '4', 5: '5 — Excellent' };

function ScoreSelect({ label, value, onChange }: { label: string; value: number; onChange: (v: number) => void }) {
  return (
    <label className="text-xs text-desk-dim flex flex-col gap-1">
      {label}
      <select className="input" value={value} onChange={e => onChange(Number(e.target.value))}>
        {[1, 2, 3, 4, 5].map(n => <option key={n} value={n}>{SCORE_LABELS[n]}</option>)}
      </select>
    </label>
  );
}

/**
 * Instructor-only grading/review modal: a snapshot of the student's
 * portfolio, scorecard and blotter, a rubric-based grade form, and inline
 * comments on individual trades. Opened by clicking "Review" on a
 * leaderboard row in ClassroomView; never lets the instructor place or
 * cancel orders -- everything here is read-only except the grade and the
 * comment composer.
 */
export function StudentReviewPanel() {
  const { dataService } = useServices();
  const state = useModelState<InstructorModel, InstructorState>(ModelIds.instructor, m => m.state);
  const { reviewing, reviewGrade, reviewComments } = state;

  const [form, setForm] = useState<GradeInput>({
    riskManagementScore: 3, disciplineScore: 3, diversificationScore: 3, overallScore: 3, feedback: '',
  });
  const [saving, setSaving] = useState(false);
  const [expandedOrderId, setExpandedOrderId] = useState<number | null>(null);
  const [newComment, setNewComment] = useState('');
  const [posting, setPosting] = useState(false);

  useEffect(() => {
    if (reviewGrade?.exists) {
      setForm({
        riskManagementScore: reviewGrade.riskManagementScore ?? 3,
        disciplineScore: reviewGrade.disciplineScore ?? 3,
        diversificationScore: reviewGrade.diversificationScore ?? 3,
        overallScore: reviewGrade.overallScore ?? 3,
        feedback: reviewGrade.feedback ?? '',
      });
    }
  }, [reviewGrade]);

  if (!reviewing) return null;
  const { portfolio, scorecard } = reviewing;

  const toggleOrder = (orderId: number) => {
    if (expandedOrderId === orderId) { setExpandedOrderId(null); return; }
    setExpandedOrderId(orderId);
    if (!reviewComments[orderId]) void dataService.loadReviewComments(orderId);
  };

  const postComment = () => {
    if (!newComment.trim() || expandedOrderId == null) return;
    setPosting(true);
    dataService.addReviewComment(expandedOrderId, newComment.trim())
      .then(() => setNewComment(''))
      .finally(() => setPosting(false));
  };

  const saveGrade = () => {
    setSaving(true);
    dataService.saveGrade(reviewing.accountId, form).finally(() => setSaving(false));
  };

  return (
    <div className="fixed inset-0 z-40 bg-black/50 flex items-start sm:items-center justify-center p-3 overflow-y-auto"
         role="dialog" aria-modal="true" aria-label={`Reviewing ${reviewing.displayName}`}
         onClick={e => { if (e.target === e.currentTarget) dataService.closeStudentReview(); }}>
      <div className="panel w-full max-w-3xl my-6">
        <div className="panel-title flex items-center justify-between">
          <span>Reviewing — {reviewing.displayName}</span>
          <button className="btn text-xs" aria-label="Close review panel" onClick={() => dataService.closeStudentReview()}>✕</button>
        </div>

        <div className="p-4 space-y-4">
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-sm">
            <div><div className="text-desk-dim text-xs uppercase">Equity</div><div className="num font-semibold">{fmtMoney(portfolio.equity, 0)}</div></div>
            <div><div className="text-desk-dim text-xs uppercase">Return</div><div className={`num font-semibold ${pnlCls(portfolio.totalReturnPct)}`}>{fmtNum(portfolio.totalReturnPct, 1)}%</div></div>
            <div><div className="text-desk-dim text-xs uppercase">Closed trades</div><div className="num font-semibold">{scorecard.totalTrades}</div></div>
            <div><div className="text-desk-dim text-xs uppercase">Win rate</div><div className="num font-semibold">{scorecard.winRatePct.toFixed(1)}%</div></div>
          </div>

          <div>
            <div className="text-xs uppercase tracking-wide text-desk-dim mb-2">Rubric</div>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
              <ScoreSelect label="Risk management" value={form.riskManagementScore}
                           onChange={v => setForm(f => ({ ...f, riskManagementScore: v }))} />
              <ScoreSelect label="Discipline" value={form.disciplineScore}
                           onChange={v => setForm(f => ({ ...f, disciplineScore: v }))} />
              <ScoreSelect label="Diversification" value={form.diversificationScore}
                           onChange={v => setForm(f => ({ ...f, diversificationScore: v }))} />
              <ScoreSelect label="Overall" value={form.overallScore}
                           onChange={v => setForm(f => ({ ...f, overallScore: v }))} />
            </div>
            <label htmlFor="feedback" className="text-xs text-desk-dim block mt-3 mb-1">Written feedback</label>
            <textarea id="feedback" className="input w-full" rows={3} value={form.feedback}
                      onChange={e => setForm(f => ({ ...f, feedback: e.target.value }))}
                      placeholder="e.g. Good use of stop levels this week; watch position sizing on FX trades." />
            <div className="flex items-center gap-2 mt-2">
              <button className="btn btn-accent text-xs" disabled={saving} onClick={saveGrade}>
                {saving ? 'Saving…' : reviewGrade?.exists ? 'Update grade' : 'Save grade'}
              </button>
              {reviewGrade?.exists && reviewGrade.updatedAt &&
                <span className="text-xs text-desk-dim">last graded {fmtSimTime(reviewGrade.updatedAt)}</span>}
            </div>
          </div>

          <div>
            <div className="text-xs uppercase tracking-wide text-desk-dim mb-2">
              Blotter — click a trade to comment
            </div>
            <div className="overflow-x-auto max-h-64 overflow-y-auto border border-desk-border rounded-lg">
              <table className="tbl num">
                <thead><tr><th>Symbol</th><th>Side</th><th className="!text-right">Qty</th><th>Status</th></tr></thead>
                <tbody>
                  {reviewing.blotter.map(o => (
                    <React.Fragment key={o.orderId}>
                      <tr className="cursor-pointer hover:bg-desk-bg/50" onClick={() => toggleOrder(o.orderId)}>
                        <td className="font-medium">{o.symbol}</td>
                        <td className={o.side === 'BUY' ? 'text-desk-up' : 'text-desk-down'}>{o.side}</td>
                        <td className="text-right">{fmtNum(o.qty, 2)}</td>
                        <td>{o.status}</td>
                      </tr>
                      {expandedOrderId === o.orderId && (
                        <tr>
                          <td colSpan={4} className="bg-desk-bg/40 !text-left">
                            <div className="p-2 space-y-2">
                              {(reviewComments[o.orderId] ?? []).map(c => (
                                <div key={c.id} className="text-xs">
                                  <span className="font-semibold">{c.instructorName}</span>
                                  <span className="text-desk-dim ml-2">{fmtSimTime(c.createdAt)}</span>
                                  <div>{c.comment}</div>
                                </div>
                              ))}
                              {(reviewComments[o.orderId] ?? []).length === 0 && (
                                <div className="text-xs text-desk-dim">No comments yet on this trade.</div>
                              )}
                              <div className="flex gap-2">
                                <label htmlFor="newComment" className="sr-only">New comment</label>
                                <input id="newComment" className="input flex-1 text-xs" value={newComment}
                                       onChange={e => setNewComment(e.target.value)}
                                       placeholder="Add a comment on this trade…" />
                                <button className="btn text-xs" disabled={posting || !newComment.trim()}
                                        onClick={postComment}>Post</button>
                              </div>
                            </div>
                          </td>
                        </tr>
                      )}
                    </React.Fragment>
                  ))}
                  {reviewing.blotter.length === 0 &&
                    <tr><td colSpan={4} className="text-desk-dim">No trades yet.</td></tr>}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
