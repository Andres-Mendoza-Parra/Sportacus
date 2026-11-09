import { useCallback, useState } from "react";

import { todayIso } from "../constants/labels";
import { BlockLog, BlockView, SessionLogRequest } from "../services/types";

/** Valores de los chequeos de antes y después de la sesión. */
export interface CheckValues {
  pain?: number;
  mood?: number;
  rpe?: number;
  distanceKm?: number;
  durationMin?: number;
}

/** Entradas iniciales de un bloque: una por serie (reps/hold) o una por bloque (resto). */
export function initialEntries(block: BlockView): BlockLog[] {
  const p = block.prescription;
  if (p.type === "reps") {
    return Array.from({ length: Math.max(1, p.sets ?? 1) }, (_, i) => ({
      setIndex: i,
      reps: p.repsMax ?? p.reps ?? undefined,
      loadKg: p.loadKg ?? undefined,
      rir: p.rir ?? undefined,
      completed: false,
    }));
  }
  if (p.type === "hold") {
    return Array.from({ length: Math.max(1, p.sets ?? 1) }, (_, i) => ({ setIndex: i, holdS: p.holdS ?? undefined, completed: false }));
  }
  if (p.type === "distance") return [{ distanceM: p.distanceM ?? undefined, completed: false }];
  return [{ completed: false }];
}

/**
 * Registro local de la sesión en curso: entradas por bloque, dolor por bloque y chequeos.
 * `build` arma el SessionLogRequest (docs/16 §5) con planBlockId y movementKey en cada entrada.
 */
export function useSessionLog() {
  const [entries, setEntries] = useState<Record<string, BlockLog[]>>({});
  const [blockPain, setBlockPain] = useState<Record<string, number>>({});
  const [pre, setPre] = useState<CheckValues>({});
  const [post, setPost] = useState<CheckValues>({});

  const entriesFor = useCallback((block: BlockView) => entries[block.id] ?? initialEntries(block), [entries]);

  const setBlockEntries = useCallback((blockId: string, next: BlockLog[]) => {
    setEntries((prev) => ({ ...prev, [blockId]: next }));
  }, []);

  const resetBlock = useCallback((blockId: string) => {
    setEntries((prev) => {
      const next = { ...prev };
      delete next[blockId];
      return next;
    });
  }, []);

  const setPain = useCallback((blockId: string, pain: number) => setBlockPain((prev) => ({ ...prev, [blockId]: pain })), []);

  /** Distancia total registrada en los bloques (m), útil para precargar el chequeo final. */
  const loggedDistanceM = useCallback(
    (blocks: BlockView[]) =>
      blocks.reduce((sum, b) => sum + (entries[b.id] ?? []).reduce((s, e) => s + (e.completed && e.distanceM ? e.distanceM : 0), 0), 0),
    [entries],
  );

  const build = useCallback(
    (sessionId: string, title: string, blocks: BlockView[]): SessionLogRequest => {
      const logs: BlockLog[] = blocks.flatMap((block) => {
        const list = entries[block.id] ?? initialEntries(block).map((e) => ({ ...e, completed: false }));
        const pain = blockPain[block.id];
        return list.map((e) => ({
          ...e,
          planBlockId: block.id,
          movementKey: block.movementKey ?? undefined,
          pain: pain ?? e.pain,
          // Una serie no hecha no lleva valores: así el mundo no la cuenta como trabajo.
          ...(e.completed ? {} : { reps: undefined, loadKg: undefined, rir: undefined, holdS: undefined, durationS: undefined, distanceM: undefined }),
        }));
      });
      const blocksDistance = loggedDistanceM(blocks);
      const distanceM = post.distanceKm != null ? Math.round(post.distanceKm * 1000) : blocksDistance > 0 ? blocksDistance : undefined;
      return {
        planSessionId: sessionId,
        title,
        performedOn: todayIso(),
        durationMin: post.durationMin,
        rpe: post.rpe,
        painBefore: pre.pain,
        painAfter: post.pain,
        moodBefore: pre.mood,
        moodAfter: post.mood,
        distanceM,
        blocks: logs,
      };
    },
    [entries, blockPain, pre, post, loggedDistanceM],
  );

  return { entriesFor, setBlockEntries, resetBlock, blockPain, setPain, pre, setPre, post, setPost, loggedDistanceM, build };
}
