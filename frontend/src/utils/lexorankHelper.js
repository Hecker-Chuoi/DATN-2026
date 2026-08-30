/**
 * lexorankHelper.js - Frontend LexoRank O(1) Helper for Optimistic Drag-and-Drop
 * Matches Jira-grade Base-36 Lexicographical Indexing.
 */

export const DEFAULT_BUCKET = '0';
export const INITIAL_RANK = '0|i00000:';
const MID_CHAR = 'i';
const MIN_CHAR = '0';
const MAX_CHAR = 'z';

/**
 * Clean rank string by removing trailing colon
 */
function cleanRank(rank) {
  if (!rank) return '';
  let r = rank.trim();
  if (r.endsWith(':')) {
    r = r.slice(0, -1);
  }
  return r;
}

/**
 * Extract bucket from rank string
 */
function extractBucket(rank) {
  if (rank && rank.includes('|')) {
    return rank.split('|')[0];
  }
  return DEFAULT_BUCKET;
}

/**
 * Extract value from rank string
 */
function extractValue(clean) {
  if (clean && clean.includes('|')) {
    return clean.split('|')[1];
  }
  return clean || '';
}

/**
 * Format bucket and value back to Jira standard rank string
 */
function formatRank(bucket, value) {
  return `${bucket}|${value}:`;
}

/**
 * Initial rank for new lists or items
 */
export function calculateInitialRank() {
  return INITIAL_RANK;
}

/**
 * Calculates rank when dragging item to the top of list
 */
export function calculateBefore(nextRank) {
  if (!nextRank) return calculateInitialRank();

  const cleaned = cleanRank(nextRank);
  const bucket = extractBucket(nextRank);
  const value = extractValue(cleaned);

  let resultValue = '';
  let foundMid = false;

  for (let i = 0; i < value.length; i++) {
    const c = value.charCodeAt(i);
    const minCode = MIN_CHAR.charCodeAt(0);

    if (!foundMid) {
      if (c > minCode) {
        const midCode = minCode + Math.floor((c - minCode) / 2);
        if (midCode > minCode) {
          resultValue += String.fromCharCode(midCode);
          foundMid = true;
        } else {
          resultValue += MIN_CHAR;
        }
      } else {
        resultValue += MIN_CHAR;
      }
    } else {
      resultValue += MID_CHAR;
      break;
    }
  }

  if (!foundMid) {
    resultValue = `${MIN_CHAR}${MID_CHAR}`;
  }

  let result = formatRank(bucket, resultValue);
  if (result >= nextRank) {
    result = formatRank(bucket, `0${value}`);
  }
  return result;
}

/**
 * Calculates rank when dragging item to the bottom of list
 */
export function calculateAfter(prevRank) {
  if (!prevRank) return calculateInitialRank();

  const cleaned = cleanRank(prevRank);
  const bucket = extractBucket(prevRank);
  const value = extractValue(cleaned);

  let resultValue = '';
  let foundMid = false;

  for (let i = 0; i < value.length; i++) {
    const c = value.charCodeAt(i);
    const maxCode = MAX_CHAR.charCodeAt(0);

    if (!foundMid) {
      if (c < maxCode) {
        const midCode = c + Math.floor((maxCode - c + 1) / 2);
        if (midCode > c) {
          resultValue += String.fromCharCode(midCode);
          foundMid = true;
        } else {
          resultValue += String.fromCharCode(c);
        }
      } else {
        resultValue += MAX_CHAR;
      }
    } else {
      break;
    }
  }

  if (!foundMid) {
    resultValue += MID_CHAR;
  }

  let result = formatRank(bucket, resultValue);
  if (result <= prevRank) {
    result = formatRank(bucket, `${value}${MID_CHAR}`);
  }
  return result;
}

/**
 * Calculates midpoint rank between two adjacent items
 */
export function calculateBetween(prevRank, nextRank) {
  if (!prevRank && !nextRank) return calculateInitialRank();
  if (!prevRank) return calculateBefore(nextRank);
  if (!nextRank) return calculateAfter(prevRank);

  if (prevRank >= nextRank) {
    // If inconsistent order, append fallback
    return formatRank(extractBucket(prevRank), `${extractValue(cleanRank(prevRank))}${MID_CHAR}`);
  }

  const bucket = extractBucket(prevRank);
  const pVal = extractValue(cleanRank(prevRank));
  const nVal = extractValue(cleanRank(nextRank));

  const maxLen = Math.max(pVal.length, nVal.length);
  let midVal = '';

  for (let i = 0; i < maxLen; i++) {
    const cP = i < pVal.length ? pVal.charCodeAt(i) : MIN_CHAR.charCodeAt(0);
    const cN = i < nVal.length ? nVal.charCodeAt(i) : MAX_CHAR.charCodeAt(0);

    if (cP === cN) {
      midVal += String.fromCharCode(cP);
    } else if (cN - cP > 1) {
      const midChar = String.fromCharCode(cP + Math.floor((cN - cP) / 2));
      midVal += midChar;
      return formatRank(bucket, midVal);
    } else {
      // cN - cP === 1
      midVal += String.fromCharCode(cP);
      for (let j = i + 1; j < pVal.length; j++) {
        const nextP = pVal.charCodeAt(j);
        const maxCode = MAX_CHAR.charCodeAt(0);
        if (nextP < maxCode) {
          const stepCode = nextP + Math.floor((maxCode - nextP + 1) / 2);
          if (stepCode > nextP) {
            midVal += String.fromCharCode(stepCode);
            return formatRank(bucket, midVal);
          } else {
            midVal += String.fromCharCode(nextP);
          }
        } else {
          midVal += MAX_CHAR;
        }
      }
      midVal += MID_CHAR;
      return formatRank(bucket, midVal);
    }
  }

  midVal += MID_CHAR;
  let result = formatRank(bucket, midVal);
  if (result <= prevRank || result >= nextRank) {
    result = formatRank(bucket, `${pVal}${MID_CHAR}`);
  }
  return result;
}

/**
 * Compare function for sorting items by rank ascending
 */
export function compareRanks(rankA, rankB) {
  if (!rankA && !rankB) return 0;
  if (!rankA) return -1;
  if (!rankB) return 1;
  return rankA.localeCompare(rankB);
}

/**
 * Sorts array of work items by rank ASC
 */
export function sortItemsByRank(items) {
  if (!Array.isArray(items)) return [];
  return [...items].sort((a, b) => compareRanks(a.rank, b.rank));
}
