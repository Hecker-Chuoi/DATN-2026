/**
 * optimisticMove.js - Phep toan di chuyen the giua cac nhom (thuan tuy, khong React).
 *
 * Tach rieng khoi component de:
 *  - kiem thu duoc bang unit test,
 *  - dung chung cho ca Backlog (Sprint <-> Backlog) lan Board (cot <-> cot),
 *  - hoan tac chinh xac tung the khi server tu choi.
 *
 * Quy uoc: moi "container" la mot object co `items: []`; ham nhan va tra ve MANG container,
 * khong dot bien du lieu goc.
 */
import { calculateBetween } from './lexorankHelper.js';

/**
 * Tinh ket qua di chuyen mot the.
 *
 * @returns {{containers, moved, prevItemRank, nextItemRank, newRank, snapshot}}
 */
export function computeMove(containers, { itemId, fromKey, toKey, toIndex, keyOf, patch = {} }) {
  const next = containers.map((c) => ({ ...c, items: [...(c.items || [])] }));

  const fromIdx = next.findIndex((c) => keyOf(c) === fromKey);
  const toIdx = next.findIndex((c) => keyOf(c) === toKey);
  if (fromIdx === -1 || toIdx === -1) return null;

  const oldIndex = next[fromIdx].items.findIndex((i) => i.id === itemId);
  if (oldIndex === -1) return null;

  const original = next[fromIdx].items[oldIndex];
  // Snapshot chi cua RIENG the nay: khoi phuc toan bo state se de len thao tac keo song song.
  const snapshot = {
    itemId,
    fromKey,
    fromIndex: oldIndex,
    prevFields: { rank: original.rank, version: original.version, ...pick(original, Object.keys(patch)) },
  };

  next[fromIdx].items.splice(oldIndex, 1);

  const target = next[toIdx].items;
  const insertAt = clamp(toIndex ?? target.length, 0, target.length);

  // Tinh rank SAU khi cat ra, de hai the ke ben la vi tri cuoi cung that su.
  const prevItemRank = target[insertAt - 1]?.rank ?? null;
  const nextItemRank = target[insertAt]?.rank ?? null;
  const newRank = calculateBetween(prevItemRank, nextItemRank);

  const moved = { ...original, ...patch, rank: newRank };
  target.splice(insertAt, 0, moved);

  return { containers: next, moved, prevItemRank, nextItemRank, newRank, snapshot };
}

/** Tra the ve dung cho cu khi server tu choi. `keyOf` phai giong luc goi computeMove. */
export function revertMove(containers, snapshot, keyOf) {
  if (!snapshot) return containers;
  const next = containers.map((c) => ({ ...c, items: [...(c.items || [])] }));

  let removed = null;
  for (const c of next) {
    const idx = c.items.findIndex((i) => i.id === snapshot.itemId);
    if (idx !== -1) {
      removed = c.items.splice(idx, 1)[0];
      break;
    }
  }
  if (!removed) return containers;

  const backIdx = next.findIndex((c) => keyOf(c) === snapshot.fromKey);
  const restored = { ...removed, ...snapshot.prevFields };
  if (backIdx === -1) return containers;

  const at = clamp(snapshot.fromIndex, 0, next[backIdx].items.length);
  next[backIdx].items.splice(at, 0, restored);
  return next;
}

/**
 * Ap su that tu server len mot the (rank/version/status moi).
 * BAT BUOC goi sau khi API tra ve: neu khong, lan keo ke tiep se gui version cu va bi 409.
 */
export function patchItem(containers, itemId, fields) {
  return containers.map((c) => ({
    ...c,
    items: (c.items || []).map((i) => (i.id === itemId ? { ...i, ...fields } : i)),
  }));
}

function clamp(v, min, max) {
  return Math.max(min, Math.min(max, v));
}
function pick(obj, keys) {
  const out = {};
  for (const k of keys) out[k] = obj[k];
  return out;
}
