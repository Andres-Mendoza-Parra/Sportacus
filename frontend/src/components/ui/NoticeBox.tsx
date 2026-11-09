import React from "react";
import { StyleProp, Text, View, ViewStyle } from "react-native";

import { Colors, useTheme } from "../../theme";

export type NoticeKind = "info" | "warn" | "danger" | "success";

const ICONS: Record<NoticeKind, string> = { info: "💡", warn: "⚠️", danger: "⛔", success: "✅" };

/** Normaliza tonos del backend ("good", "bad", "warning"…) a uno de los 4 tonos de aviso. */
export function toNoticeKind(tone: string | null | undefined): NoticeKind {
  switch (tone) {
    case "warn":
    case "warning":
    case "amarillo":
      return "warn";
    case "danger":
    case "bad":
    case "rojo":
      return "danger";
    case "success":
    case "good":
    case "verde":
      return "success";
    default:
      return "info";
  }
}

export function noticeColors(kind: NoticeKind, colors: Colors): { fg: string; bg: string } {
  switch (kind) {
    case "warn":
      return { fg: colors.warn, bg: colors.warnSoft };
    case "danger":
      return { fg: colors.danger, bg: colors.dangerSoft };
    case "success":
      return { fg: colors.success, bg: colors.successSoft };
    default:
      return { fg: colors.info, bg: colors.infoSoft };
  }
}

interface Props {
  tone?: NoticeKind;
  title?: string;
  body: string;
  icon?: string;
  style?: StyleProp<ViewStyle>;
  children?: React.ReactNode;
}

/** Aviso con tono (info / warn / danger / success). */
export default function NoticeBox({ tone = "info", title, body, icon, style, children }: Props) {
  const { colors } = useTheme();
  const c = noticeColors(tone, colors);
  return (
    <View
      accessibilityRole="alert"
      style={[{ flexDirection: "row", gap: 10, padding: 12, borderRadius: 14, backgroundColor: c.bg, borderLeftWidth: 4, borderLeftColor: c.fg }, style]}
    >
      <Text style={{ fontSize: 16 }}>{icon ?? ICONS[tone]}</Text>
      <View style={{ flex: 1, gap: 2 }}>
        {title ? <Text style={{ fontSize: 13, fontWeight: "700", color: c.fg }}>{title}</Text> : null}
        <Text style={{ fontSize: 13, color: colors.text, lineHeight: 19 }}>{body}</Text>
        {children}
      </View>
    </View>
  );
}
