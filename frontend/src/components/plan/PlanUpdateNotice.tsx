import React from "react";
import { StyleProp, Text, ViewStyle } from "react-native";

import { PlanView } from "../../services/types";
import { useTheme } from "../../theme";
import NoticeBox from "../ui/NoticeBox";
import PressableScale from "../ui/PressableScale";

const DAY_MS = 24 * 60 * 60 * 1000;

/** ¿El plan se actualizó solo hace menos de `withinMs`? (sin fecha: se asume reciente). */
export function planUpdatedRecently(plan: Pick<PlanView, "updateReason" | "updatedAt"> | null | undefined, withinMs = DAY_MS): boolean {
  if (!plan?.updateReason) return false;
  if (!plan.updatedAt) return true;
  const t = Date.parse(plan.updatedAt);
  return Number.isNaN(t) || Date.now() - t <= withinMs;
}

interface Props {
  reason: string;
  onDismiss?: () => void;
  style?: StyleProp<ViewStyle>;
}

/** Aviso "Actualizamos tu plan…" cuando cambian los datos que lo afectan (plan dinámico). */
export default function PlanUpdateNotice({ reason, onDismiss, style }: Props) {
  const { colors } = useTheme();
  return (
    <NoticeBox tone="info" icon="🔄" title="Tu plan se actualizó" body={reason} style={style}>
      {onDismiss ? (
        <PressableScale
          onPress={onDismiss}
          accessibilityRole="button"
          accessibilityLabel="Entendido, ocultar aviso"
          style={{ alignSelf: "flex-start", minHeight: 44, justifyContent: "center" }}
        >
          <Text style={{ fontSize: 12, fontWeight: "700", color: colors.info }}>Entendido</Text>
        </PressableScale>
      ) : null}
    </NoticeBox>
  );
}
