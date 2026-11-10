import React from "react";
import { View } from "react-native";
import Animated, { FadeInDown, FadeOutDown } from "react-native-reanimated";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { useAuth } from "../../context/AuthContext";
import { useOnline } from "../../services/connectivity";
import { usePendingCount } from "../../services/pendingSessions";
import { OfflineBanner } from "../ui/States";

/**
 * Aviso flotante (abajo, sin tapar la navegación) cuando no hay conexión o quedan entrenamientos
 * guardados en el dispositivo por enviar. No recibe toques: no estorba.
 */
export default function ConnectionBanner({ bottomOffset = 70 }: { bottomOffset?: number }) {
  const online = useOnline();
  const pending = usePendingCount();
  const { token } = useAuth();
  const insets = useSafeAreaInsets();
  if (online && (pending === 0 || !token)) return null;
  return (
    <View pointerEvents="none" style={{ position: "absolute", left: 12, right: 12, bottom: insets.bottom + bottomOffset, alignItems: "center", zIndex: 900 }}>
      <Animated.View entering={FadeInDown.duration(220)} exiting={FadeOutDown.duration(180)} style={{ width: "100%", maxWidth: 520 }}>
        <OfflineBanner offline={!online} pending={pending} style={{ marginBottom: 0, boxShadow: "0px 4px 14px rgba(0,0,0,0.18)" }} />
      </Animated.View>
    </View>
  );
}
