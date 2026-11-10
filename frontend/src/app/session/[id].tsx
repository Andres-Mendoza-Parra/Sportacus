import { useKeepAwake } from "expo-keep-awake";
import { router, useLocalSearchParams } from "expo-router";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { BackHandler, KeyboardAvoidingView, Platform, ScrollView, Text, View } from "react-native";
import Animated, { ZoomIn } from "react-native-reanimated";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import BlockPlayer from "../../components/session/BlockPlayer";
import ChecksForm from "../../components/session/ChecksForm";
import SessionPreview from "../../components/session/SessionPreview";
import SessionResultView from "../../components/session/SessionResultView";
import { useConfirm, useToast } from "../../components/ui/Feedback";
import GradientButton from "../../components/ui/GradientButton";
import NoticeBox from "../../components/ui/NoticeBox";
import ScreenHeader, { HeaderIconButton } from "../../components/ui/ScreenHeader";
import { ScreenSkeleton } from "../../components/ui/Skeleton";
import { ErrorState, OfflineBanner } from "../../components/ui/States";
import { formatDate } from "../../constants/labels";
import { useAuth } from "../../context/AuthContext";
import { useWorld } from "../../context/WorldContext";
import { useLoader, useReloadOnFocus } from "../../hooks/useLoader";
import { useSessionLog } from "../../hooks/useSessionLog";
import { useVoice } from "../../hooks/useVoice";
import { track } from "../../services/analytics";
import { errorStatus, extractErrorMessage } from "../../services/api";
import { useOnline } from "../../services/connectivity";
import { forgetSessionClientId, isRetryable, rememberSessionClientId, savePendingSession } from "../../services/pendingSessions";
import { BlockView, SessionResult } from "../../services/types";
import { uuidV4 } from "../../services/uuid";
import { fetchSession, logSession, swapBlock } from "../../services/worldService";
import { useTheme } from "../../theme";

type Phase = "preview" | "prechecks" | "blocks" | "postchecks" | "result" | "saved";

/** Mantiene la pantalla encendida mientras está montado (solo durante la sesión). */
function KeepAwake() {
  useKeepAwake();
  return null;
}

/** "Hoy, un poco más suave" → se usa tal cual; "Un poco más suave" → "Hoy: un poco más suave". */
function todayTitle(label: string, factor: number): string {
  const pct = `−${Math.round((1 - factor) * 100)} % de volumen`;
  const text = /^hoy\b/i.test(label.trim()) ? label.trim() : `Hoy: ${label.trim().charAt(0).toLowerCase()}${label.trim().slice(1)}`;
  return `${text} (${pct})`;
}

/**
 * Reproductor de sesión genérico: vista previa → chequeos previos → bloques (renderizados por tipo de
 * prescripción) → chequeos finales → registro → resultado. Todo lo decide `ui` + `prescription.type`.
 * El registro lleva un `clientId` (UUID creado al empezar): si el envío falla por conexión, sesión
 * vencida o error del servidor, se guarda en el dispositivo y se reintenta sin duplicar.
 */
export default function SessionScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();
  const toast = useToast();
  const confirm = useConfirm();
  const { user } = useAuth();
  const { active, invalidate } = useWorld();
  const online = useOnline();
  const voice = useVoice();
  const log = useSessionLog();
  const loader = useLoader(() => fetchSession(String(id)), [id]);

  const [phase, setPhase] = useState<Phase>("preview");
  const [index, setIndex] = useState(0);
  const [swapped, setSwapped] = useState<Record<string, BlockView>>({});
  const [swappingId, setSwappingId] = useState<string | null>(null);
  const [startedAt, setStartedAt] = useState<number | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<SessionResult | null>(null);
  const [expired, setExpired] = useState(false);
  const submittingRef = useRef(false);
  /** UUID del registro de esta sesión (se crea al tocar "Empezar"). */
  const clientIdRef = useRef<string | null>(null);
  /** Mundo dueño de la sesión, fijado al empezar (por si se cambia de mundo a mitad de camino). */
  const worldRef = useRef<string | null>(null);

  const blocks = useMemo(
    () => [...(loader.data?.session.blocks ?? [])].sort((a, b) => a.orderIndex - b.orderIndex).map((b) => swapped[b.id] ?? b),
    [loader.data, swapped],
  );
  const playing = phase === "prechecks" || phase === "blocks" || phase === "postchecks";

  // Al volver del detalle de un bloque (donde se puede cambiar el ejercicio) se refrescan los bloques:
  // en la vista previa, todo; durante la sesión, solo los que cambiaron de movimiento (y su registro).
  const phaseRef = useRef(phase);
  const blocksRef = useRef(blocks);
  useEffect(() => {
    phaseRef.current = phase;
    blocksRef.current = blocks;
  });
  const reloadSession = loader.reload;
  const resetBlock = log.resetBlock;
  useReloadOnFocus(
    useCallback(async () => {
      const current = phaseRef.current;
      if (current === "preview") {
        await reloadSession();
        setSwapped({}); // el servidor ya trae los cambios de ejercicio guardados
        return;
      }
      if (current !== "prechecks" && current !== "blocks" && current !== "postchecks") return;
      try {
        const fresh = await fetchSession(String(id));
        const shown = new Map(blocksRef.current.map((b) => [b.id, b]));
        const changed = fresh.session.blocks.filter((b) => {
          const before = shown.get(b.id);
          return before != null && (before.movementKey ?? null) !== (b.movementKey ?? null);
        });
        if (changed.length === 0) return;
        setSwapped((prev) => {
          const next = { ...prev };
          for (const b of changed) next[b.id] = b;
          return next;
        });
        for (const b of changed) resetBlock(b.id);
      } catch {
        // sin conexión: se sigue con los bloques que ya se ven
      }
    }, [id, reloadSession, resetBlock]),
  );

  function leave() {
    if (router.canGoBack()) router.back();
    else router.replace("/home");
  }

  async function exit() {
    if (submittingRef.current) return;
    if (playing) {
      const ok = await confirm({
        title: "¿Salir de la sesión?",
        message: "Perderás lo que llevas registrado de esta sesión.",
        confirmText: "Salir",
        destructive: true,
      });
      if (!ok) return;
      track("session_abandoned", { world: worldRef.current ?? undefined, phase });
      if (loader.data) forgetSessionClientId(loader.data.session.id);
    }
    if (phase === "result" || phase === "saved") {
      invalidate();
      router.dismissTo("/home");
      return;
    }
    leave();
  }

  useEffect(() => {
    const sub = BackHandler.addEventListener("hardwareBackPress", () => {
      exit();
      return true;
    });
    return () => sub.remove();
  });

  if (loader.loading) return <ScreenSkeleton cards={4} />;
  if (!loader.data) {
    return (
      <View style={{ flex: 1, backgroundColor: colors.bg }}>
        <ScreenHeader title="Sesión" />
        <ErrorState message={loader.error ?? "No encontramos esta sesión."} onRetry={loader.reload} />
      </View>
    );
  }

  const { session, ui, today } = loader.data;
  const softDay = today && today.factor < 0.95 ? today : null;
  const block = blocks[index];

  async function swap(target: BlockView) {
    setSwappingId(target.id);
    try {
      const next = await swapBlock(target.id, worldRef.current ?? active?.key ?? null);
      setSwapped((prev) => ({ ...prev, [target.id]: next }));
      log.resetBlock(target.id);
      toast.success(`Cambiamos «${target.title}» por «${next.title}»`);
    } catch (error) {
      toast.error(extractErrorMessage(error));
    } finally {
      setSwappingId(null);
    }
  }

  async function start() {
    if (session.done) {
      const ok = await confirm({
        title: "¿Repetir esta sesión?",
        message: session.lastDoneOn
          ? `Ya la hiciste el ${formatDate(session.lastDoneOn)}. Si la repites, se registrará como una sesión nueva.`
          : "Ya la hiciste esta semana. Si la repites, se registrará como una sesión nueva.",
        confirmText: "Repetir",
      });
      if (!ok) return;
    }
    const clientId = uuidV4();
    clientIdRef.current = clientId;
    worldRef.current = active?.key ?? null;
    rememberSessionClientId(session.id, clientId);
    track("session_started", { world: worldRef.current ?? undefined, repeat: session.done });
    setStartedAt(Date.now());
    setIndex(0);
    setPhase(ui.preChecks.length > 0 ? "prechecks" : "blocks");
  }

  function toPostChecks() {
    const minutes = startedAt ? Math.max(1, Math.round((Date.now() - startedAt) / 60000)) : undefined;
    const km = log.loggedDistanceM(blocks) / 1000;
    log.setPost({
      ...log.post,
      durationMin: log.post.durationMin ?? (ui.postChecks.includes("duration") ? minutes : undefined),
      distanceKm: log.post.distanceKm ?? (ui.postChecks.includes("distance") && km > 0 ? Math.round(km * 100) / 100 : undefined),
    });
    if (ui.postChecks.length > 0) setPhase("postchecks");
    else submit(minutes);
  }

  async function submit(fallbackMinutes?: number) {
    if (submittingRef.current) return;
    if (ui.postChecks.includes("rpe") && log.post.rpe == null) {
      toast.info("Cuéntanos qué tan dura fue la sesión (RPE).");
      return;
    }
    const request = log.build(session.id, session.title, blocks);
    if (request.durationMin == null) {
      request.durationMin = fallbackMinutes ?? (startedAt ? Math.max(1, Math.round((Date.now() - startedAt) / 60000)) : undefined);
    }
    const clientId = clientIdRef.current ?? uuidV4();
    clientIdRef.current = clientId;
    request.clientId = clientId;
    const world = worldRef.current;
    submittingRef.current = true;
    setSubmitting(true);
    try {
      const saved = await logSession(request, world ?? undefined);
      setResult(saved);
      forgetSessionClientId(session.id);
      const goal = saved.highlights.find((h) => h.includes("Meta lograda"));
      if (goal) toast.success(goal, { durationMs: 6000 });
      setPhase("result");
    } catch (error) {
      if (isRetryable(error) && user) {
        // Nada se pierde: se guarda con su clientId y se envía después (sin duplicar).
        await savePendingSession({ clientId, userId: user.userId, world, title: session.title, savedAt: Date.now(), request });
        setExpired(errorStatus(error) === 401);
        setPhase("saved");
      } else {
        toast.error(extractErrorMessage(error));
      }
    } finally {
      submittingRef.current = false;
      setSubmitting(false);
    }
  }

  function next() {
    if (submittingRef.current) return;
    if (index < blocks.length - 1) setIndex(index + 1);
    else toPostChecks();
  }

  function finishToHome() {
    invalidate();
    router.dismissTo("/home");
  }

  const subtitle =
    phase === "blocks"
      ? `Bloque ${index + 1} de ${blocks.length}`
      : phase === "prechecks"
        ? "Antes de empezar"
        : phase === "postchecks"
          ? "Para terminar"
          : phase === "result"
            ? "¡Buen trabajo!"
            : phase === "saved"
              ? "Entrenamiento guardado"
              : session.focus;

  return (
    <KeyboardAvoidingView style={{ flex: 1, backgroundColor: colors.bg }} behavior={Platform.OS === "ios" ? "padding" : undefined}>
      {playing ? <KeepAwake /> : null}
      <ScreenHeader
        title={session.title}
        subtitle={subtitle}
        onBack={exit}
        right={phase === "blocks" ? <HeaderIconButton icon={voice.enabled ? "🔊" : "🔇"} label={voice.enabled ? "Silenciar voz" : "Activar voz"} onPress={voice.toggle} /> : null}
      />
      <ScrollView contentContainerStyle={{ padding: 16, paddingBottom: 24 }} keyboardShouldPersistTaps="handled">
        {!online && phase !== "saved" ? <OfflineBanner /> : null}
        {softDay && (phase === "preview" || phase === "prechecks") ? (
          <NoticeBox
            tone="info"
            icon="☀️"
            title={todayTitle(softDay.label, softDay.factor)}
            body={softDay.reasons.length > 0 ? softDay.reasons.join(" · ") : "Según tu check-in, hoy conviene ir más suave."}
            style={{ marginBottom: 14 }}
          />
        ) : null}
        {phase === "preview" ? <SessionPreview session={session} blocks={blocks} ui={ui} onSwap={swap} swappingId={swappingId} /> : null}
        {phase === "prechecks" ? <ChecksForm stage="pre" checks={ui.preChecks} values={log.pre} onChange={log.setPre} /> : null}
        {phase === "blocks" && block ? (
          <BlockPlayer
            block={block}
            entries={log.entriesFor(block)}
            onChange={(entries) => log.setBlockEntries(block.id, entries)}
            ui={ui}
            speak={voice.speak}
            pain={log.blockPain[block.id]}
            onPain={(p) => log.setPain(block.id, p)}
            onSwap={() => swap(block)}
            swapping={swappingId === block.id}
          />
        ) : null}
        {phase === "postchecks" ? <ChecksForm stage="post" checks={ui.postChecks} values={log.post} onChange={log.setPost} /> : null}
        {phase === "result" && result ? <SessionResultView result={result} /> : null}
        {phase === "saved" ? <SavedOffline expired={expired} /> : null}
      </ScrollView>

      <View style={{ flexDirection: "row", gap: 10, padding: 16, paddingBottom: 16 + insets.bottom, backgroundColor: colors.card, borderTopWidth: 1, borderTopColor: colors.border }}>
        {phase === "preview" ? (
          <GradientButton title={session.done ? "↻ Repetir sesión" : "▶ Empezar"} onPress={start} disabled={blocks.length === 0} style={{ flex: 1 }} />
        ) : null}
        {phase === "prechecks" ? <GradientButton title="Continuar" onPress={() => setPhase("blocks")} style={{ flex: 1 }} /> : null}
        {phase === "blocks" ? (
          <>
            <GradientButton title="←" variant="ghost" onPress={() => setIndex(index - 1)} disabled={index === 0 || submitting} style={{ flex: 0.5 }} />
            <GradientButton title="Saltar bloque" variant="ghost" onPress={next} disabled={submitting} style={{ flex: 1 }} />
            <GradientButton
              title={index < blocks.length - 1 ? "Siguiente →" : "Terminar"}
              onPress={next}
              loading={submitting}
              disabled={submitting}
              style={{ flex: 1.3 }}
            />
          </>
        ) : null}
        {phase === "postchecks" ? (
          <>
            <GradientButton title="Volver" variant="ghost" onPress={() => setPhase("blocks")} disabled={submitting} style={{ flex: 1 }} />
            <GradientButton title="Guardar sesión" onPress={() => submit()} loading={submitting} disabled={submitting} style={{ flex: 1.6 }} />
          </>
        ) : null}
        {phase === "result" ? <GradientButton title="Volver al inicio" onPress={finishToHome} style={{ flex: 1 }} /> : null}
        {phase === "saved" ? (
          expired ? (
            <GradientButton title="Volver a entrar" onPress={() => router.replace("/login")} style={{ flex: 1 }} />
          ) : (
            <GradientButton title="Volver al inicio" onPress={finishToHome} style={{ flex: 1 }} />
          )
        ) : null}
      </View>
    </KeyboardAvoidingView>
  );
}

/** Registro guardado en el dispositivo (sin conexión, sesión vencida o error del servidor). */
function SavedOffline({ expired }: { expired: boolean }) {
  const { colors } = useTheme();
  return (
    <View style={{ gap: 14 }}>
      <Animated.View entering={ZoomIn.springify().damping(14)} style={{ alignItems: "center", paddingVertical: 16 }}>
        <Text style={{ fontSize: 60 }}>💾</Text>
        <Text style={{ fontSize: 22, fontWeight: "800", color: colors.text, marginTop: 6, textAlign: "center" }}>
          {expired ? "Tu sesión expiró" : "Guardamos tu entrenamiento"}
        </Text>
      </Animated.View>
      <NoticeBox
        tone={expired ? "warn" : "info"}
        icon={expired ? "🔑" : "📡"}
        title={expired ? "Vuelve a entrar: guardamos tu entrenamiento" : "Lo enviaremos en cuanto se pueda"}
        body={
          expired
            ? "Por seguridad tu sesión se cerró. Tu entrenamiento quedó guardado en este dispositivo y se registrará apenas vuelvas a entrar, sin duplicarse."
            : "No pudimos conectarnos con el servidor. Tu entrenamiento quedó guardado en este dispositivo y lo registraremos automáticamente cuando vuelva la conexión."
        }
      />
    </View>
  );
}
