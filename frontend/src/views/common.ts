import { useSyncModelWithSelector, syncModelWithSelectorOptions } from 'esp-js-react';
import { ImmutableModel } from 'esp-js-polimer';

/** Subscribe a component to a slice of a polimer model by model id. */
export function useModelState<TModel extends ImmutableModel, TSelected>(
  modelId: string, selector: (m: TModel) => TSelected): TSelected {
  return useSyncModelWithSelector<TModel, TSelected>(
    selector, syncModelWithSelectorOptions<TSelected>().setModelId(modelId));
}

export const fmtMoney = (x: number | null | undefined, dp = 2) =>
  x == null ? '—' : x.toLocaleString(undefined, { minimumFractionDigits: dp, maximumFractionDigits: dp });

export const fmtNum = (x: number | null | undefined, dp = 4) =>
  x == null ? '—' : x.toLocaleString(undefined, { minimumFractionDigits: 0, maximumFractionDigits: dp });

export const fmtPct = (x: number | null | undefined, dp = 2) =>
  x == null ? '—' : `${x >= 0 ? '+' : ''}${x.toFixed(dp)}%`;

/** tailwind color class for a signed value */
export const pnlCls = (x: number | null | undefined) =>
  x == null || x === 0 ? 'text-desk-dim' : x > 0 ? 'text-desk-up' : 'text-desk-down';

export const fmtSimTime = (iso: string | undefined | null) => {
  if (!iso) return '—';
  const d = new Date(iso);
  return `${d.toISOString().slice(0, 10)} ${d.toISOString().slice(11, 16)}`;
};
