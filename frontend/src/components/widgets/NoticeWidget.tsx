import { router } from "expo-router";
import React from "react";
import { Text } from "react-native";
import Animated, { FadeInDown } from "react-native-reanimated";

import { TypedWidget } from "../../services/types";
import { useTheme } from "../../theme";
import NoticeBox, { toNoticeKind } from "../ui/NoticeBox";
import PressableScale from "../ui/PressableScale";

/** Avisos que llevan a una pantalla al tocarlos (por clave del widget). */
const NOTICE_ROUTES: Record<string, { go: () => void; cta: string }> = {
  salud_pendiente: { go: () => router.push("/health"), cta: "Hacer mi chequeo de salud →" },
};

/** Aviso destacado del mundo (p. ej. "Modo prudente" sin chequeo de salud). */
export default function NoticeWidget({ widget, delay = 0 }: { widget: TypedWidget<"notice">; delay?: number }) {
  const { colors } = useTheme();
  const action = NOTICE_ROUTES[widget.key];
  const box = (
    <NoticeBox tone={toNoticeKind(widget.data.tone)} title={widget.title ?? undefined} body={widget.data.body}>
      {action ? <Text style={{ fontSize: 12, fontWeight: "700", color: colors.primary, marginTop: 6 }}>{action.cta}</Text> : null}
    </NoticeBox>
  );
  return (
    <Animated.View entering={FadeInDown.delay(delay).duration(380)}>
      {action ? (
        <PressableScale onPress={action.go} accessibilityRole="button" accessibilityLabel={widget.title ?? widget.data.body}>
          {box}
        </PressableScale>
      ) : (
        box
      )}
    </Animated.View>
  );
}
