import { router } from "expo-router";
import React, { useState } from "react";
import { Text, View } from "react-native";
import Animated, { FadeIn } from "react-native-reanimated";

import { NextItem, TypedWidget } from "../../services/types";
import { useTheme, withAlpha } from "../../theme";
import NextItemCard from "../profile360/NextItemCard";
import ProgressRing from "../profile360/ProgressRing";
import ReadinessCard from "../profile360/ReadinessCard";
import AnimatedBar from "../ui/AnimatedBar";
import PressableScale from "../ui/PressableScale";
import WidgetFrame from "./WidgetFrame";

type WidgetProps<T extends "profile_ring" | "question" | "checkin" | "world_suggestions" | "readiness"> = {
  widget: TypedWidget<T>;
  delay?: number;
};

/** Anillo del Perfil 360 con las áreas menos completas; toca → /profile360. */
export function ProfileRingWidget({ widget, delay }: WidgetProps<"profile_ring">) {
  const { colors } = useTheme();
  const d = widget.data;
  const areas = [...d.areas].sort((a, b) => a.pct - b.pct).slice(0, 4);
  return (
    <WidgetFrame title={widget.title ?? "Tu Perfil 360"} subtitle={widget.subtitle} delay={delay}>
      <PressableScale onPress={() => router.push("/profile360")} accessibilityRole="button" accessibilityLabel="Abrir mi Perfil 360">
        <View style={{ flexDirection: "row", alignItems: "center", gap: 16 }}>
          <ProgressRing pct={d.pct} size={92} />
          <View style={{ flex: 1, gap: 4 }}>
            <Text style={{ fontSize: 15, fontWeight: "700", color: colors.text }}>{d.label}</Text>
            <Text style={{ fontSize: 12, color: colors.textMuted, lineHeight: 17 }}>
              Cuéntanos a tu ritmo: cada dato afina tu plan. Nada es obligatorio.
            </Text>
            <Text style={{ fontSize: 12, fontWeight: "700", color: colors.primary, marginTop: 4 }}>Completar mi perfil →</Text>
          </View>
        </View>
        {areas.length > 0 ? (
          <View style={{ gap: 8, marginTop: 14 }}>
            {areas.map((a) => (
              <View key={a.key} style={{ flexDirection: "row", alignItems: "center", gap: 8 }}>
                <Text style={{ fontSize: 14, width: 22 }}>{a.icon}</Text>
                <Text style={{ fontSize: 12, color: colors.text, width: 120 }} numberOfLines={1}>
                  {a.name}
                </Text>
                <AnimatedBar pct={a.pct} height={6} style={{ flex: 1 }} />
                <Text style={{ fontSize: 11, color: colors.textFaint, width: 34, textAlign: "right" }}>{`${Math.round(a.pct)}%`}</Text>
              </View>
            ))}
          </View>
        ) : null}
      </PressableScale>
    </WidgetFrame>
  );
}

/** Siguiente mejor pregunta, respondible en línea. Al terminar muestra la siguiente o un agradecimiento. */
export function QuestionWidget({ widget, delay }: WidgetProps<"question">) {
  const { colors } = useTheme();
  const [item, setItem] = useState<NextItem | null>(widget.data.item);
  const [done, setDone] = useState(false);
  // Si el inicio se recarga con otra pregunta, se muestra la nueva.
  const [sourceId, setSourceId] = useState(widget.data.item.id);
  if (sourceId !== widget.data.item.id) {
    setSourceId(widget.data.item.id);
    setItem(widget.data.item);
    setDone(false);
  }

  return (
    <WidgetFrame title={widget.title ?? "Una pregunta rápida"} subtitle={widget.subtitle} delay={delay}>
      {item ? (
        <NextItemCard
          key={item.id}
          item={item}
          onDone={(next) => {
            setItem(next);
            if (!next) setDone(true);
          }}
        />
      ) : done ? (
        <Animated.View entering={FadeIn.duration(250)} style={{ alignItems: "center", gap: 6, paddingVertical: 8 }}>
          <Text style={{ fontSize: 30 }}>🙌</Text>
          <Text style={{ fontSize: 14, fontWeight: "700", color: colors.text }}>¡Listo por ahora!</Text>
          <PressableScale onPress={() => router.push("/profile360")} accessibilityRole="button" style={{ minHeight: 44, justifyContent: "center", paddingHorizontal: 8 }}>
            <Text style={{ fontSize: 12, fontWeight: "700", color: colors.primary }}>Ver mi Perfil 360 →</Text>
          </PressableScale>
        </Animated.View>
      ) : null}
    </WidgetFrame>
  );
}

/** Check-in diario: invita a hacerlo o muestra cómo estás hoy. */
export function CheckinWidget({ widget, delay }: WidgetProps<"checkin">) {
  const { colors } = useTheme();
  const d = widget.data;
  if (d.done && d.readiness) {
    return (
      <WidgetFrame title={widget.title ?? "Cómo estás hoy"} subtitle={widget.subtitle} delay={delay}>
        <ReadinessCard readiness={d.readiness} compact />
        <PressableScale onPress={() => router.push("/checkin")} accessibilityRole="button" style={{ alignSelf: "flex-start", marginTop: 4, minHeight: 44, justifyContent: "center", paddingRight: 8 }}>
          <Text style={{ fontSize: 12, fontWeight: "700", color: colors.primary }}>Actualizar mi check-in →</Text>
        </PressableScale>
      </WidgetFrame>
    );
  }
  return (
    <WidgetFrame title={widget.title} subtitle={widget.subtitle} delay={delay}>
      <PressableScale onPress={() => router.push("/checkin")} accessibilityRole="button" style={{ flexDirection: "row", alignItems: "center", gap: 12, minHeight: 48 }}>
        <View style={{ width: 46, height: 46, borderRadius: 15, backgroundColor: colors.primarySoft, alignItems: "center", justifyContent: "center" }}>
          <Text style={{ fontSize: 22 }}>☀️</Text>
        </View>
        <View style={{ flex: 1 }}>
          <Text style={{ fontSize: 15, fontWeight: "700", color: colors.text }}>{d.done ? "Check-in hecho" : "¿Cómo amaneciste?"}</Text>
          <Text style={{ fontSize: 12, color: colors.textMuted, marginTop: 2 }}>
            {d.done ? "Gracias por contarnos cómo estás." : "6 deslizadores, 20 segundos. Ajustamos tu sesión de hoy."}
          </Text>
        </View>
        <Text style={{ fontSize: 20, color: colors.textFaint }}>›</Text>
      </PressableScale>
    </WidgetFrame>
  );
}

/** Mundos sugeridos para explorar desde Mi Base. */
export function WorldSuggestionsWidget({ widget, delay }: WidgetProps<"world_suggestions">) {
  const { colors } = useTheme();
  const items = widget.data.items;
  if (items.length === 0) return null;
  return (
    <WidgetFrame title={widget.title ?? "Mundos para ti"} subtitle={widget.subtitle} delay={delay}>
      <View style={{ gap: 10 }}>
        {items.map((w) => (
          <PressableScale
            key={w.key}
            onPress={() => router.push({ pathname: "/onboarding/world/[key]", params: { key: w.key } })}
            accessibilityRole="button"
            accessibilityLabel={`Ver ${w.name}`}
            style={{ flexDirection: "row", gap: 12, padding: 12, borderRadius: 14, backgroundColor: withAlpha(w.color, 0.09), borderWidth: 1, borderColor: withAlpha(w.color, 0.25) }}
          >
            <View style={{ width: 42, height: 42, borderRadius: 13, backgroundColor: withAlpha(w.color, 0.2), alignItems: "center", justifyContent: "center" }}>
              <Text style={{ fontSize: 21 }}>{w.icon}</Text>
            </View>
            <View style={{ flex: 1, gap: 2 }}>
              <Text style={{ fontSize: 14, fontWeight: "700", color: colors.text }}>{w.name}</Text>
              <Text style={{ fontSize: 12, color: colors.textMuted }} numberOfLines={2}>
                {w.tagline}
              </Text>
              {w.reasons.slice(0, 2).map((r) => (
                <Text key={r} style={{ fontSize: 12, color: colors.text, lineHeight: 17 }}>
                  {`✓ ${r}`}
                </Text>
              ))}
            </View>
            <Text style={{ fontSize: 20, color: colors.textFaint, alignSelf: "center" }}>›</Text>
          </PressableScale>
        ))}
        <PressableScale onPress={() => router.push("/onboarding/choose")} accessibilityRole="button" style={{ alignSelf: "center", minHeight: 44, justifyContent: "center", paddingHorizontal: 8 }}>
          <Text style={{ fontSize: 12, fontWeight: "700", color: colors.primary }}>Explorar todos los mundos →</Text>
        </PressableScale>
      </View>
    </WidgetFrame>
  );
}

/** Aviso de hoy tras el check-in. */
export function ReadinessWidget({ widget, delay }: WidgetProps<"readiness">) {
  return (
    <WidgetFrame title={widget.title ?? "Tu día de hoy"} subtitle={widget.subtitle} delay={delay}>
      <ReadinessCard readiness={widget.data} compact />
    </WidgetFrame>
  );
}
