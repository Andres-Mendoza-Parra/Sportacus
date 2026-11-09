import { router } from "expo-router";
import React from "react";
import { Text, View } from "react-native";

import { todayWeekday, weekdayName } from "../../constants/labels";
import { TypedWidget } from "../../services/types";
import { useTheme } from "../../theme";
import GradientButton from "../ui/GradientButton";
import WidgetFrame from "./WidgetFrame";

/** Próxima sesión del plan con botón para empezarla; sin sesión = semana completa. */
export default function NextSessionWidget({ widget, delay }: { widget: TypedWidget<"next_session">; delay?: number }) {
  const { colors } = useTheme();
  const session = widget.data.session;

  if (!session) {
    return (
      <WidgetFrame title={widget.title} delay={delay}>
        <View style={{ alignItems: "center", paddingVertical: 8, gap: 4 }}>
          <Text style={{ fontSize: 34 }}>🎉</Text>
          <Text style={{ fontSize: 16, fontWeight: "700", color: colors.text }}>Semana completa</Text>
          <Text style={{ fontSize: 13, color: colors.textMuted, textAlign: "center" }}>
            Hiciste todas las sesiones de esta semana. Descansa: la próxima semana te espera.
          </Text>
          <GradientButton title="Ver mi plan" variant="ghost" onPress={() => router.push("/plan")} style={{ alignSelf: "stretch", marginTop: 10 }} />
        </View>
      </WidgetFrame>
    );
  }

  const isToday = session.weekday === todayWeekday();
  const day = isToday ? "Hoy" : weekdayName(session.weekday);
  const meta = [day, session.estMinutes ? `~${session.estMinutes} min` : null, `${session.blocks.length} bloques`].filter(Boolean).join(" · ");

  return (
    <WidgetFrame title={widget.title} subtitle={widget.subtitle} delay={delay}>
      <View style={{ backgroundColor: colors.primarySoft, borderRadius: 14, padding: 14, gap: 4 }}>
        <Text style={{ fontSize: 12, fontWeight: "700", color: colors.primary, textTransform: "uppercase", letterSpacing: 0.5 }}>{meta}</Text>
        <Text style={{ fontSize: 18, fontWeight: "800", color: colors.text }}>{session.title}</Text>
        {session.focus ? (
          <Text style={{ fontSize: 13, color: colors.textMuted, lineHeight: 18 }} numberOfLines={3}>
            {session.focus}
          </Text>
        ) : null}
      </View>
      <GradientButton
        title={session.done ? "Repetir sesión" : "Empezar ▶"}
        onPress={() => router.push({ pathname: "/session/[id]", params: { id: session.id } })}
        style={{ marginTop: 12 }}
      />
    </WidgetFrame>
  );
}
