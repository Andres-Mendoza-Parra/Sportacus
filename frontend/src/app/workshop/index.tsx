import { Image } from "expo-image";
import { router, useFocusEffect } from "expo-router";
import { useCallback, useState } from "react";
import { FlatList, ScrollView, Text, TextInput, View } from "react-native";

import { useConfirm, useToast } from "../../components/ui/Feedback";
import GradientButton from "../../components/ui/GradientButton";
import GradientHeader from "../../components/ui/GradientHeader";
import PressableScale from "../../components/ui/PressableScale";
import { ScreenSkeleton } from "../../components/ui/Skeleton";
import Sheet from "../../components/ui/Sheet";
import Stepper from "../../components/ui/Stepper";
import { extractErrorMessage } from "../../services/api";
import {
  Routine,
  RoutineExercise,
  addRoutineDay,
  fetchRoutine,
  removeRoutineDay,
  removeRoutineExercise,
  updateRoutineExercise,
} from "../../services/routineService";
import { Theme, useThemedStyles, useTheme } from "../../theme";

const WEEK_QUICK = ["Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"];

export default function WorkshopScreen() {
  const styles = useThemedStyles(createStyles);
  const { colors } = useTheme();
  const toast = useToast();
  const confirm = useConfirm();

  const [routine, setRoutine] = useState<Routine | null>(null);
  const [dayIndex, setDayIndex] = useState(0);
  const [busy, setBusy] = useState(false);
  const [addDayOpen, setAddDayOpen] = useState(false);
  const [newDayLabel, setNewDayLabel] = useState("");

  const load = useCallback(() => {
    fetchRoutine()
      .then(setRoutine)
      .catch((error) => toast.error(extractErrorMessage(error)));
  }, [toast]);

  useFocusEffect(
    useCallback(() => {
      load();
    }, [load]),
  );

  if (!routine) return <ScreenSkeleton cards={4} />;

  const totalDays = routine.days.length;
  const safeIndex = Math.min(dayIndex, Math.max(0, totalDays - 1));
  const day = routine.days[safeIndex];

  async function run(action: () => Promise<Routine>): Promise<Routine | null> {
    setBusy(true);
    try {
      const next = await action();
      setRoutine(next);
      return next;
    } catch (error) {
      toast.error(extractErrorMessage(error));
      return null;
    } finally {
      setBusy(false);
    }
  }

  async function handleAddDay() {
    const label = newDayLabel.trim();
    if (!label) return;
    setAddDayOpen(false);
    setNewDayLabel("");
    const next = await run(() => addRoutineDay(label));
    if (next) setDayIndex(next.days.length - 1);
  }

  async function handleRemoveDay() {
    if (totalDays <= 1) {
      toast.info("Debe quedar al menos un día en tu rutina.");
      return;
    }
    const ok = await confirm({
      title: `¿Quitar "${day.dayLabel}"?`,
      message: "Se eliminarán también los ejercicios de este día.",
      confirmText: "Quitar",
      destructive: true,
    });
    if (!ok) return;
    const next = await run(() => removeRoutineDay(safeIndex));
    if (next) setDayIndex(Math.min(safeIndex, next.days.length - 1));
  }

  async function handleRemoveExercise(ex: RoutineExercise) {
    const ok = await confirm({
      title: `¿Quitar "${ex.name}" de tu rutina?`,
      confirmText: "Quitar",
      destructive: true,
    });
    if (!ok) return;
    await run(() => removeRoutineExercise(ex.exerciseRecommendationId));
  }

  function handleChangeExercise(ex: RoutineExercise, patch: Partial<Record<"sets" | "reps" | "durationSec", number>>) {
    run(() =>
      updateRoutineExercise(ex.exerciseRecommendationId, {
        sets: patch.sets ?? ex.sets ?? undefined,
        reps: patch.reps ?? ex.reps ?? undefined,
        durationSec: patch.durationSec ?? ex.durationSec ?? undefined,
      }),
    );
  }

  return (
    <View style={styles.bg}>
      <GradientHeader style={{ paddingBottom: 14 }}>
        <View style={styles.headTop}>
          <PressableScale style={styles.backBtn} onPress={() => (router.canGoBack() ? router.back() : router.replace("/routine"))}>
            <Text style={styles.backText}>←</Text>
          </PressableScale>
          <View style={{ flex: 1 }}>
            <Text style={styles.title}>Taller de rutina</Text>
            <Text style={styles.subtitle}>Agrega, quita y ajusta los ejercicios de cada día</Text>
          </View>
        </View>
      </GradientHeader>

      <View style={styles.dayBar}>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.dayTabs}>
          {routine.days.map((d, idx) => (
            <PressableScale
              key={d.dayLabel + idx}
              style={[styles.dayTab, idx === safeIndex && styles.dayTabActive]}
              onPress={() => setDayIndex(idx)}
              scaleTo={0.94}
            >
              <Text style={[styles.dayTabText, idx === safeIndex && styles.dayTabTextActive]} numberOfLines={1}>
                {d.dayLabel}
              </Text>
            </PressableScale>
          ))}
          <PressableScale style={styles.addDayBtn} onPress={() => setAddDayOpen(true)} scaleTo={0.9}>
            <Text style={styles.addDayText}>＋</Text>
          </PressableScale>
        </ScrollView>
        <PressableScale onPress={handleRemoveDay} style={styles.removeDayBtn} disabled={busy} scaleTo={0.9}>
          <Text style={{ fontSize: 16 }}>🗑</Text>
        </PressableScale>
      </View>

      <FlatList
        data={day?.exercises ?? []}
        keyExtractor={(e) => e.exerciseRecommendationId}
        contentContainerStyle={{ padding: 16, gap: 10, paddingBottom: 32 }}
        ListEmptyComponent={<Text style={styles.empty}>Este día no tiene ejercicios todavía.</Text>}
        renderItem={({ item }) => (
          <ExerciseRow ex={item} busy={busy} onChange={(patch) => handleChangeExercise(item, patch)} onRemove={() => handleRemoveExercise(item)} />
        )}
        ListFooterComponent={
          <PressableScale
            style={styles.addExerciseBtn}
            onPress={() => router.push({ pathname: "/workshop/pick-exercise", params: { day: String(safeIndex) } })}
          >
            <Text style={styles.addExerciseText}>＋ Agregar ejercicio</Text>
          </PressableScale>
        }
      />

      <Sheet visible={addDayOpen} title="Nuevo día" onClose={() => setAddDayOpen(false)}>
        <View style={{ padding: 16, paddingTop: 4, gap: 14 }}>
          <TextInput
            style={styles.dayInput}
            placeholder="Nombre del día (ej. Push, Pierna, Lun)"
            placeholderTextColor={colors.textFaint}
            value={newDayLabel}
            onChangeText={setNewDayLabel}
            autoFocus
          />
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 8 }}>
            {WEEK_QUICK.map((w) => (
              <PressableScale key={w} style={styles.quickChip} onPress={() => setNewDayLabel(w)} scaleTo={0.94}>
                <Text style={styles.quickChipText}>{w}</Text>
              </PressableScale>
            ))}
          </ScrollView>
          <GradientButton title="Agregar día" onPress={handleAddDay} disabled={!newDayLabel.trim() || busy} />
        </View>
      </Sheet>
    </View>
  );
}

function ExerciseRow({
  ex,
  busy,
  onChange,
  onRemove,
}: {
  ex: RoutineExercise;
  busy: boolean;
  onChange: (patch: Partial<Record<"sets" | "reps" | "durationSec", number>>) => void;
  onRemove: () => void;
}) {
  const styles = useThemedStyles(createStyles);
  const timed = ex.durationSec !== null;

  return (
    <View style={styles.exCard}>
      <View style={styles.exTop}>
        {ex.imageUrl ? (
          <Image source={{ uri: ex.imageUrl }} style={styles.exThumb} contentFit="cover" />
        ) : (
          <View style={[styles.exThumb, styles.exThumbEmpty]}>
            <Text style={{ fontSize: 24 }}>{ex.icon}</Text>
          </View>
        )}
        <View style={{ flex: 1 }}>
          <Text style={styles.exName} numberOfLines={2}>
            {ex.name}
          </Text>
          <Text style={styles.exMeta}>{[ex.muscleGroupTag, ex.difficultyTag].join(" · ")}</Text>
        </View>
        <PressableScale onPress={onRemove} disabled={busy} style={styles.removeBtn} scaleTo={0.9}>
          <Text style={{ fontSize: 17 }}>🗑</Text>
        </PressableScale>
      </View>
      <View style={styles.steppers}>
        <Stepper
          label="Series"
          value={ex.sets ?? 3}
          min={1}
          max={10}
          disabled={busy}
          onChange={(v) => onChange({ sets: v })}
        />
        {timed ? (
          <Stepper
            label="Segundos"
            value={ex.durationSec ?? 45}
            min={5}
            max={300}
            step={5}
            disabled={busy}
            onChange={(v) => onChange({ durationSec: v })}
          />
        ) : (
          <Stepper
            label="Reps"
            value={ex.reps ?? 10}
            min={1}
            max={50}
            disabled={busy}
            onChange={(v) => onChange({ reps: v })}
          />
        )}
      </View>
    </View>
  );
}

const createStyles = ({ colors, shadow }: Theme) => ({
  bg: { flex: 1, backgroundColor: colors.bg },
  headTop: { flexDirection: "row" as const, alignItems: "center" as const, gap: 12 },
  backBtn: { width: 38, height: 38, borderRadius: 19, backgroundColor: "rgba(255,255,255,0.2)", alignItems: "center" as const, justifyContent: "center" as const },
  backText: { color: "#fff", fontSize: 18 },
  title: { color: "#fff", fontSize: 22, fontWeight: "800" as const },
  subtitle: { color: "rgba(255,255,255,0.8)", fontSize: 13 },
  dayBar: { flexDirection: "row" as const, alignItems: "center" as const, paddingLeft: 16, gap: 8 },
  dayTabs: { paddingVertical: 12, paddingRight: 8, gap: 8 },
  dayTab: { paddingVertical: 8, paddingHorizontal: 16, borderRadius: 16, backgroundColor: colors.card, borderWidth: 1, borderColor: colors.border, maxWidth: 140 },
  dayTabActive: { backgroundColor: colors.primary, borderColor: colors.primary },
  dayTabText: { color: colors.textMuted, fontSize: 13, fontWeight: "700" as const },
  dayTabTextActive: { color: "#fff" },
  addDayBtn: { width: 36, height: 36, borderRadius: 18, backgroundColor: colors.cardAlt, alignItems: "center" as const, justifyContent: "center" as const },
  addDayText: { color: colors.primary, fontSize: 18, fontWeight: "800" as const },
  removeDayBtn: { width: 36, height: 36, borderRadius: 18, alignItems: "center" as const, justifyContent: "center" as const, marginRight: 16 },
  empty: { color: colors.textMuted, textAlign: "center" as const, marginTop: 40 },
  exCard: { backgroundColor: colors.card, borderRadius: 18, padding: 12, borderWidth: 1, borderColor: colors.border, ...shadow },
  exTop: { flexDirection: "row" as const, alignItems: "center" as const, gap: 12 },
  exThumb: { width: 56, height: 56, borderRadius: 12, backgroundColor: colors.cardAlt },
  exThumbEmpty: { alignItems: "center" as const, justifyContent: "center" as const },
  exName: { color: colors.text, fontSize: 14, fontWeight: "700" as const },
  exMeta: { color: colors.textMuted, fontSize: 12, marginTop: 2 },
  removeBtn: { padding: 8 },
  steppers: { flexDirection: "row" as const, gap: 20, marginTop: 12, paddingTop: 12, borderTopWidth: 1, borderTopColor: colors.border },
  addExerciseBtn: { marginTop: 4, paddingVertical: 14, borderRadius: 16, backgroundColor: colors.primarySoft, alignItems: "center" as const },
  addExerciseText: { color: colors.primary, fontWeight: "700" as const, fontSize: 14 },
  dayInput: { backgroundColor: colors.cardAlt, borderRadius: 14, paddingVertical: 12, paddingHorizontal: 14, color: colors.text, fontSize: 14 },
  quickChip: { paddingVertical: 7, paddingHorizontal: 14, borderRadius: 14, backgroundColor: colors.cardAlt },
  quickChipText: { color: colors.textMuted, fontSize: 12, fontWeight: "600" as const },
});
