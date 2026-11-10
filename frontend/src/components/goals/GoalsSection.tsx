import React, { useState } from "react";
import { ActivityIndicator, Text, View } from "react-native";

import { formatDate } from "../../constants/labels";
import { useWorld } from "../../context/WorldContext";
import { useLoader } from "../../hooks/useLoader";
import { extractErrorMessage } from "../../services/api";
import { completeGoal, fetchGoals, reopenGoal } from "../../services/goalsService";
import { GoalView } from "../../services/types";
import { Theme, useThemedStyles, useTheme } from "../../theme";
import AnimatedBar from "../ui/AnimatedBar";
import { Card } from "../ui/Card";
import { useConfirm, useToast } from "../ui/Feedback";
import GradientButton from "../ui/GradientButton";
import { EmptyState, ErrorState } from "../ui/States";
import GoalCelebration from "./GoalCelebration";

const STATUS: Record<string, { label: string; icon: string }> = {
  activa: { label: "En curso", icon: "🎯" },
  lograda: { label: "Lograda", icon: "🏆" },
  abandonada: { label: "En pausa", icon: "⏸️" },
};

/**
 * "Tus metas" de un mundo: progreso (si se puede medir), marcar como lograda o reabrir, y celebración.
 * Sin `world` usa el mundo activo.
 */
export default function GoalsSection({ world, emptyHint = true }: { world?: string | null; emptyHint?: boolean }) {
  const { version, invalidate } = useWorld();
  const toast = useToast();
  const confirm = useConfirm();
  const goals = useLoader(() => fetchGoals(world), [world, version]);
  const [busy, setBusy] = useState<string | null>(null);
  const [celebrate, setCelebrate] = useState<GoalView | null>(null);

  function replace(updated: GoalView) {
    goals.setData((prev) => (prev ?? []).map((g) => (g.id === updated.id ? updated : g)));
  }

  async function complete(goal: GoalView) {
    setBusy(goal.id);
    try {
      const updated = await completeGoal(goal.id);
      replace(updated);
      setCelebrate(updated);
    } catch (error) {
      toast.error(extractErrorMessage(error));
    } finally {
      setBusy(null);
    }
  }

  async function reopen(goal: GoalView) {
    const ok = await confirm({
      title: "¿Reabrir esta meta?",
      message: "Volverá a estar en curso. Tu logro y tu historial se conservan.",
      confirmText: "Reabrir",
    });
    if (!ok) return;
    setBusy(goal.id);
    try {
      replace(await reopenGoal(goal.id));
      toast.success("Meta reabierta. ¡A por ella!");
    } catch (error) {
      toast.error(extractErrorMessage(error));
    } finally {
      setBusy(null);
    }
  }

  if (goals.loading && !goals.data) {
    return (
      <Card style={{ alignItems: "center" }}>
        <ActivityIndicator />
      </Card>
    );
  }
  if (!goals.data) {
    return (
      <Card>
        <ErrorState message={goals.error ?? "No pudimos cargar tus metas."} onRetry={goals.reload} style={{ padding: 8 }} />
      </Card>
    );
  }

  const list = [...goals.data].sort((a, b) => order(a.status) - order(b.status));

  return (
    <View style={{ gap: 10 }}>
      {list.length === 0 ? (
        emptyHint ? (
          <EmptyState icon="🎯" title="Aún no tienes metas aquí" body="Cuando tu mundo te proponga una meta (o la elijas al afinar tu plan), aparecerá en esta sección." />
        ) : null
      ) : (
        list.map((g, i) => (
          <GoalCard
            key={g.id}
            goal={g}
            delay={i * 50}
            busy={busy === g.id}
            disabled={busy !== null}
            onComplete={() => complete(g)}
            onReopen={() => reopen(g)}
          />
        ))
      )}
      <GoalCelebration
        goal={celebrate}
        onClose={() => {
          setCelebrate(null);
          invalidate();
        }}
      />
    </View>
  );
}

function order(status: string): number {
  return status === "activa" ? 0 : status === "lograda" ? 1 : 2;
}

interface CardProps {
  goal: GoalView;
  delay: number;
  busy: boolean;
  disabled: boolean;
  onComplete: () => void;
  onReopen: () => void;
}

function GoalCard({ goal, delay, busy, disabled, onComplete, onReopen }: CardProps) {
  const styles = useThemedStyles(createStyles);
  const { colors } = useTheme();
  const status = STATUS[goal.status] ?? STATUS.activa;
  const achieved = goal.status === "lograda";
  const pct = goal.progressPct != null ? Math.max(0, Math.min(100, goal.progressPct)) : null;
  return (
    <Card delay={delay} style={[styles.card, achieved && { borderColor: colors.success, borderWidth: 1.5 }]}>
      <View style={styles.head}>
        <Text style={{ fontSize: 22 }}>{status.icon}</Text>
        <View style={{ flex: 1 }}>
          <Text style={styles.label}>{goal.label}</Text>
          <Text style={[styles.status, achieved && { color: colors.success }]}>
            {achieved && goal.achievedAt ? `Lograda el ${formatDate(goal.achievedAt, true)}` : status.label}
            {achieved && goal.achievedBy === "auto" ? " · se marcó sola" : ""}
            {goal.auto && !achieved ? " · se marca sola al cumplirla" : ""}
          </Text>
        </View>
        {pct != null ? <Text style={styles.pct}>{`${Math.round(pct)}%`}</Text> : null}
      </View>
      {pct != null ? <AnimatedBar pct={pct} height={8} style={{ marginTop: 10 }} /> : null}
      {goal.progressText ? <Text style={styles.detail}>{goal.progressText}</Text> : null}
      {goal.targetDate ? <Text style={styles.date}>{`Fecha objetivo: ${formatDate(goal.targetDate, true)}`}</Text> : null}
      <View style={{ marginTop: 12 }}>
        {goal.status === "activa" ? (
          <GradientButton title="✓ Marcar como lograda" onPress={onComplete} loading={busy} disabled={disabled && !busy} />
        ) : (
          <GradientButton title="↺ Reabrir" variant="ghost" onPress={onReopen} loading={busy} disabled={disabled && !busy} />
        )}
      </View>
    </Card>
  );
}

const createStyles = ({ colors }: Theme) => ({
  card: { borderWidth: 1, borderColor: "transparent" },
  head: { flexDirection: "row" as const, alignItems: "center" as const, gap: 10 },
  label: { fontSize: 15, fontWeight: "700" as const, color: colors.text },
  status: { fontSize: 12, color: colors.textMuted, marginTop: 2 },
  pct: { fontSize: 18, fontWeight: "800" as const, color: colors.primary },
  detail: { fontSize: 12, color: colors.textMuted, marginTop: 8, lineHeight: 17 },
  date: { fontSize: 12, color: colors.textFaint, marginTop: 4 },
});
