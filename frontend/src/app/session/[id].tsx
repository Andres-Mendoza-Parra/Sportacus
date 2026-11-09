import { useKeepAwake } from "expo-keep-awake";
import { router, useLocalSearchParams } from "expo-router";
import { useEffect, useMemo, useState } from "react";
import { BackHandler, KeyboardAvoidingView, Platform, ScrollView, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import BlockPlayer from "../../components/session/BlockPlayer";
import ChecksForm from "../../components/session/ChecksForm";
import SessionPreview from "../../components/session/SessionPreview";
import SessionResultView from "../../components/session/SessionResultView";
import { useConfirm, useToast } from "../../components/ui/Feedback";
import GradientButton from "../../components/ui/GradientButton";
import ScreenHeader, { HeaderIconButton } from "../../components/ui/ScreenHeader";
import { ScreenSkeleton } from "../../components/ui/Skeleton";
import { ErrorState } from "../../components/ui/States";
import { useWorld } from "../../context/WorldContext";
import { useLoader } from "../../hooks/useLoader";
import { useSessionLog } from "../../hooks/useSessionLog";
import { useVoice } from "../../hooks/useVoice";
import { extractErrorMessage } from "../../services/api";
import { BlockView, SessionResult } from "../../services/types";
import { fetchSession, logSession, swapBlock } from "../../services/worldService";
import { useTheme } from "../../theme";

type Phase = "preview" | "prechecks" | "blocks" | "postchecks" | "result";

/** Mantiene la pantalla encendida mientras está montado (solo durante la sesión). */
function KeepAwake() {
  useKeepAwake();
  return null;
}

/**
 * Reproductor de sesión genérico: vista previa → chequeos previos → bloques (renderizados por tipo de
 * prescripción) → chequeos finales → registro → resultado. Todo lo decide `ui` + `prescription.type`.
 */
export default function SessionScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const { colors } = useTheme();
  const insets = useSafeAreaInsets();
  const toast = useToast();
  const confirm = useConfirm();
  const { invalidate } = useWorld();
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

  const blocks = useMemo(
    () => [...(loader.data?.session.blocks ?? [])].sort((a, b) => a.orderIndex - b.orderIndex).map((b) => swapped[b.id] ?? b),
    [loader.data, swapped],
  );
  const playing = phase === "prechecks" || phase === "blocks" || phase === "postchecks";

  async function exit() {
    if (playing) {
      const ok = await confirm({
        title: "¿Salir de la sesión?",
        message: "Perderás lo que llevas registrado de esta sesión.",
        confirmText: "Salir",
        destructive: true,
      });
      if (!ok) return;
    }
    if (phase === "result") invalidate();
    if (router.canGoBack()) router.back();
    else router.replace("/home");
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

  const { session, ui } = loader.data;
  const block = blocks[index];

  async function swap(target: BlockView) {
    setSwappingId(target.id);
    try {
      const next = await swapBlock(target.id);
      setSwapped((prev) => ({ ...prev, [target.id]: next }));
      log.resetBlock(target.id);
      toast.success(`Cambiamos «${target.title}» por «${next.title}»`);
    } catch (error) {
      toast.error(extractErrorMessage(error));
    } finally {
      setSwappingId(null);
    }
  }

  function start() {
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
    if (ui.postChecks.includes("rpe") && log.post.rpe == null) {
      toast.info("Cuéntanos qué tan dura fue la sesión (RPE).");
      return;
    }
    const request = log.build(session.id, session.title, blocks);
    if (request.durationMin == null) {
      request.durationMin = fallbackMinutes ?? (startedAt ? Math.max(1, Math.round((Date.now() - startedAt) / 60000)) : undefined);
    }
    setSubmitting(true);
    try {
      setResult(await logSession(request));
      setPhase("result");
    } catch (error) {
      toast.error(extractErrorMessage(error));
    } finally {
      setSubmitting(false);
    }
  }

  function next() {
    if (index < blocks.length - 1) setIndex(index + 1);
    else toPostChecks();
  }

  const subtitle =
    phase === "blocks" ? `Bloque ${index + 1} de ${blocks.length}` : phase === "prechecks" ? "Antes de empezar" : phase === "postchecks" ? "Para terminar" : phase === "result" ? "¡Buen trabajo!" : session.focus;

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
      </ScrollView>

      <View style={{ flexDirection: "row", gap: 10, padding: 16, paddingBottom: 16 + insets.bottom, backgroundColor: colors.card, borderTopWidth: 1, borderTopColor: colors.border }}>
        {phase === "preview" ? <GradientButton title="▶ Empezar" onPress={start} disabled={blocks.length === 0} style={{ flex: 1 }} /> : null}
        {phase === "prechecks" ? <GradientButton title="Continuar" onPress={() => setPhase("blocks")} style={{ flex: 1 }} /> : null}
        {phase === "blocks" ? (
          <>
            <GradientButton title="←" variant="ghost" onPress={() => setIndex(index - 1)} disabled={index === 0} style={{ flex: 0.5 }} />
            <GradientButton title="Saltar bloque" variant="ghost" onPress={next} style={{ flex: 1 }} />
            <GradientButton title={index < blocks.length - 1 ? "Siguiente →" : "Terminar"} onPress={next} style={{ flex: 1.3 }} />
          </>
        ) : null}
        {phase === "postchecks" ? (
          <>
            <GradientButton title="Volver" variant="ghost" onPress={() => setPhase("blocks")} disabled={submitting} style={{ flex: 1 }} />
            <GradientButton title="Guardar sesión" onPress={() => submit()} loading={submitting} style={{ flex: 1.6 }} />
          </>
        ) : null}
        {phase === "result" ? (
          <GradientButton
            title="Volver al inicio"
            onPress={() => {
              invalidate();
              router.replace("/home");
            }}
            style={{ flex: 1 }}
          />
        ) : null}
      </View>
    </KeyboardAvoidingView>
  );
}
