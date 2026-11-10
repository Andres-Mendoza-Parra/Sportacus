import { Image } from "expo-image";
import { router } from "expo-router";
import React, { useEffect, useState } from "react";
import { ActivityIndicator, Text, View } from "react-native";
import Animated, { FadeIn } from "react-native-reanimated";

import { blockTypeLabel, sideLabel } from "../../constants/labels";
import { extractErrorMessage } from "../../services/api";
import { BlockDetail, BlockView } from "../../services/types";
import { getBlockDetailCached } from "../../services/worldService";
import { useTheme } from "../../theme";
import NoticeBox from "../ui/NoticeBox";
import PressableScale from "../ui/PressableScale";
import { prescriptionSummary } from "./format";

interface Props {
  block: BlockView;
  onSwap?: () => void;
  swapping?: boolean;
}

/** Cabecera de un bloque: tipo, título, imagen, por qué, indicaciones, "Cómo hacerlo" plegable y acciones. */
export default function BlockHeader({ block, onSwap, swapping }: Props) {
  const { colors } = useTheme();
  const p = block.prescription;
  const details = [p.tempo ? `Tempo ${p.tempo}` : null, sideLabel(p.side), p.intensity ? `Intensidad: ${p.intensity}` : null].filter(
    (x): x is string => !!x,
  );

  return (
    <View style={{ gap: 10 }}>
      <Text style={{ fontSize: 11, fontWeight: "700", color: colors.primary, textTransform: "uppercase", letterSpacing: 0.8 }}>
        {blockTypeLabel(block.blockType)}
      </Text>
      <Text style={{ fontSize: 22, fontWeight: "800", color: colors.text }}>{block.title}</Text>
      <Text style={{ fontSize: 14, color: colors.textMuted }}>{prescriptionSummary(p)}</Text>

      {block.imageUrl ? (
        <Image
          source={{ uri: block.imageUrl }}
          contentFit="contain"
          accessibilityLabel={`Imagen de ${block.title}`}
          style={{ width: "100%", height: 180, borderRadius: 16, backgroundColor: "#fff" }}
        />
      ) : null}

      {block.summary ? <Text style={{ fontSize: 13, color: colors.text, lineHeight: 19 }}>💡 {block.summary}</Text> : null}

      {details.length > 0 ? (
        <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 6 }}>
          {details.map((d) => (
            <View key={d} style={{ paddingHorizontal: 10, paddingVertical: 5, borderRadius: 999, backgroundColor: colors.cardAlt }}>
              <Text style={{ fontSize: 12, color: colors.text, fontWeight: "600" }}>{d}</Text>
            </View>
          ))}
        </View>
      ) : null}

      {p.cue ? <Text style={{ fontSize: 13, color: colors.text, lineHeight: 19 }}>🎯 {p.cue}</Text> : null}
      {block.notes ? <Text style={{ fontSize: 13, color: colors.textMuted, lineHeight: 19 }}>{block.notes}</Text> : null}
      {p.painMax != null ? <NoticeBox tone="warn" body={`Detente si el dolor supera ${p.painMax}/10.`} /> : null}

      <HowTo block={block} />

      <View style={{ flexDirection: "row", gap: 8 }}>
        <PressableScale
          onPress={() => router.push({ pathname: "/block/[id]", params: { id: block.id } })}
          accessibilityRole="button"
          accessibilityLabel={`Ver detalle completo de ${block.title}`}
          style={{ flex: 1, paddingVertical: 10, borderRadius: 12, alignItems: "center", backgroundColor: colors.cardAlt }}
        >
          <Text style={{ fontSize: 13, fontWeight: "700", color: colors.primary }}>📖 Ver detalle completo</Text>
        </PressableScale>
        {onSwap && !block.progressionKey ? (
          <PressableScale
            onPress={onSwap}
            disabled={swapping}
            accessibilityRole="button"
            accessibilityLabel="Cambiar por una alternativa"
            style={{ flex: 1, paddingVertical: 10, borderRadius: 12, alignItems: "center", backgroundColor: colors.cardAlt }}
          >
            {swapping ? (
              <ActivityIndicator color={colors.primary} />
            ) : (
              <Text style={{ fontSize: 13, fontWeight: "700", color: colors.primary }}>🔄 Cambiar ejercicio</Text>
            )}
          </PressableScale>
        ) : null}
      </View>
    </View>
  );
}

type HowState = { key: string; detail?: BlockDetail; error?: string };

/** Sección plegable "Cómo hacerlo": pasos y claves, cargados al abrirla (en caché por bloque). */
function HowTo({ block }: { block: BlockView }) {
  const { colors } = useTheme();
  const [open, setOpen] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const [state, setState] = useState<HowState | null>(null);
  const key = `${block.id}:${block.movementKey ?? ""}:${attempt}`;

  useEffect(() => {
    if (!open) return;
    let alive = true;
    getBlockDetailCached(block).then(
      (detail) => alive && setState({ key, detail }),
      (error: unknown) => alive && setState({ key, error: extractErrorMessage(error) }),
    );
    return () => {
      alive = false;
    };
    // `key` resume el bloque, su movimiento y el reintento.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, key]);

  const current = state?.key === key ? state : null;
  const movement = current?.detail?.movement;
  const steps = movement?.steps ?? [];
  const cues = movement?.cues ?? [];

  return (
    <View style={{ borderRadius: 14, backgroundColor: colors.cardAlt, overflow: "hidden" }}>
      <PressableScale
        onPress={() => setOpen((o) => !o)}
        accessibilityRole="button"
        accessibilityLabel="Cómo hacerlo"
        accessibilityHint={open ? "Oculta los pasos" : "Muestra los pasos y las claves de técnica"}
        accessibilityState={{ expanded: open }}
        style={{ flexDirection: "row", alignItems: "center", paddingVertical: 12, paddingHorizontal: 14, gap: 8 }}
      >
        <Text style={{ fontSize: 15 }}>📋</Text>
        <Text style={{ flex: 1, fontSize: 14, fontWeight: "700", color: colors.text }}>Cómo hacerlo</Text>
        <Text style={{ fontSize: 13, color: colors.textMuted }}>{open ? "▲" : "▼"}</Text>
      </PressableScale>
      {open ? (
        <Animated.View entering={FadeIn.duration(200)} style={{ paddingHorizontal: 14, paddingBottom: 14, gap: 6 }}>
          {!current ? (
            <ActivityIndicator color={colors.primary} style={{ paddingVertical: 8 }} />
          ) : current.error ? (
            <View style={{ gap: 6 }}>
              <Text style={{ fontSize: 13, color: colors.danger }}>{current.error}</Text>
              <Text
                onPress={() => setAttempt((a) => a + 1)}
                accessibilityRole="button"
                style={{ fontSize: 13, fontWeight: "700", color: colors.primary }}
              >
                Reintentar
              </Text>
            </View>
          ) : steps.length + cues.length === 0 ? (
            <Text style={{ fontSize: 13, color: colors.textMuted, lineHeight: 19 }}>
              {movement?.description ?? "Este bloque no tiene pasos detallados: sigue las indicaciones de arriba."}
            </Text>
          ) : (
            <>
              {steps.length > 0 ? <Label text="Paso a paso" /> : null}
              {steps.map((step, i) => (
                <Line key={`s${i}`} bullet={`${i + 1}.`} text={step} />
              ))}
              {cues.length > 0 ? <Label text="Claves de técnica" /> : null}
              {cues.map((cue, i) => (
                <Line key={`c${i}`} bullet="✅" text={cue} />
              ))}
            </>
          )}
        </Animated.View>
      ) : null}
    </View>
  );
}

function Label({ text }: { text: string }) {
  const { colors } = useTheme();
  return (
    <Text style={{ fontSize: 11, color: colors.textFaint, fontWeight: "700", textTransform: "uppercase", letterSpacing: 0.6, marginTop: 4 }}>{text}</Text>
  );
}

function Line({ bullet, text }: { bullet: string; text: string }) {
  const { colors } = useTheme();
  return (
    <View style={{ flexDirection: "row", gap: 8, alignItems: "flex-start" }}>
      <Text style={{ fontSize: 13, color: colors.primary, fontWeight: "700", minWidth: 18 }}>{bullet}</Text>
      <Text style={{ flex: 1, fontSize: 13, color: colors.text, lineHeight: 19 }}>{text}</Text>
    </View>
  );
}
