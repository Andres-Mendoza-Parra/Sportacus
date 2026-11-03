import React, { createContext, useCallback, useContext, useMemo, useRef, useState } from "react";
import { Modal, Pressable, StyleSheet, Text, View } from "react-native";
import Animated, {
  FadeIn,
  FadeInUp,
  FadeOut,
  FadeOutUp,
  LinearTransition,
  ZoomIn,
  ZoomOut,
} from "react-native-reanimated";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { useTheme } from "../../theme";
import GradientButton from "./GradientButton";

type ToastKind = "success" | "error" | "info";

interface ToastItem {
  id: number;
  kind: ToastKind;
  message: string;
  actionLabel?: string;
  onAction?: () => void;
}

interface ToastOptions {
  actionLabel?: string;
  onAction?: () => void;
  durationMs?: number;
}

interface ToastApi {
  success: (message: string, options?: ToastOptions) => void;
  error: (message: string, options?: ToastOptions) => void;
  info: (message: string, options?: ToastOptions) => void;
}

export interface ConfirmOptions {
  title: string;
  message?: string;
  confirmText?: string;
  cancelText?: string;
  destructive?: boolean;
}

type ConfirmFn = (options: ConfirmOptions) => Promise<boolean>;

const ToastContext = createContext<ToastApi | undefined>(undefined);
const ConfirmContext = createContext<ConfirmFn | undefined>(undefined);

const ICONS: Record<ToastKind, string> = { success: "✅", error: "⚠️", info: "💡" };

/**
 * Proveedor de avisos: toasts animados arriba de la pantalla (useToast) y
 * diálogo de confirmación modal (useConfirm). Reemplaza a Alert.alert,
 * que no funciona igual en web.
 */
export function FeedbackProvider({ children }: { children: React.ReactNode }) {
  const { colors, shadow } = useTheme();
  const insets = useSafeAreaInsets();
  const [toasts, setToasts] = useState<ToastItem[]>([]);
  const nextId = useRef(1);

  const [dialog, setDialog] = useState<(ConfirmOptions & { resolve: (v: boolean) => void }) | null>(null);

  const dismiss = useCallback((id: number) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const push = useCallback(
    (kind: ToastKind, message: string, options?: ToastOptions) => {
      const id = nextId.current++;
      setToasts((prev) => [...prev.slice(-2), { id, kind, message, actionLabel: options?.actionLabel, onAction: options?.onAction }]);
      setTimeout(() => dismiss(id), options?.durationMs ?? (options?.actionLabel ? 5500 : kind === "error" ? 4500 : 3000));
    },
    [dismiss],
  );

  const toastApi = useMemo<ToastApi>(
    () => ({
      success: (m, o) => push("success", m, o),
      error: (m, o) => push("error", m, o),
      info: (m, o) => push("info", m, o),
    }),
    [push],
  );

  const confirm = useCallback<ConfirmFn>(
    (options) => new Promise<boolean>((resolve) => setDialog({ ...options, resolve })),
    [],
  );

  function closeDialog(result: boolean) {
    dialog?.resolve(result);
    setDialog(null);
  }

  const accentFor = (kind: ToastKind) =>
    kind === "success" ? colors.primary : kind === "error" ? colors.danger : colors.info;

  return (
    <ToastContext.Provider value={toastApi}>
      <ConfirmContext.Provider value={confirm}>
        {children}

        <View pointerEvents="box-none" style={[styles.toastLayer, { top: insets.top + 8 }]}>
          {toasts.map((t) => (
            <Animated.View
              key={t.id}
              entering={FadeInUp.springify().damping(18)}
              exiting={FadeOutUp.duration(200)}
              layout={LinearTransition}
              style={[
                styles.toast,
                shadow,
                { backgroundColor: colors.card, borderLeftColor: accentFor(t.kind) },
              ]}
            >
              <Text style={styles.toastIcon}>{ICONS[t.kind]}</Text>
              <Text style={[styles.toastText, { color: colors.text }]}>{t.message}</Text>
              {t.actionLabel ? (
                <Pressable
                  onPress={() => {
                    dismiss(t.id);
                    t.onAction?.();
                  }}
                  hitSlop={8}
                >
                  <Text style={{ color: colors.primary, fontWeight: "700", fontSize: 13 }}>{t.actionLabel}</Text>
                </Pressable>
              ) : (
                <Pressable onPress={() => dismiss(t.id)} hitSlop={8}>
                  <Text style={{ color: colors.textFaint, fontSize: 14 }}>✕</Text>
                </Pressable>
              )}
            </Animated.View>
          ))}
        </View>

        <Modal visible={dialog !== null} transparent animationType="none" onRequestClose={() => closeDialog(false)} statusBarTranslucent>
          {dialog ? (
            <View style={styles.dialogRoot}>
              <Animated.View entering={FadeIn.duration(160)} exiting={FadeOut.duration(120)} style={StyleSheet.absoluteFill}>
                <Pressable style={[StyleSheet.absoluteFill, { backgroundColor: colors.overlay }]} onPress={() => closeDialog(false)} />
              </Animated.View>
              <Animated.View
                entering={ZoomIn.springify().damping(16)}
                exiting={ZoomOut.duration(120)}
                style={[styles.dialog, shadow, { backgroundColor: colors.card }]}
              >
                <Text style={{ fontSize: 17, fontWeight: "700", color: colors.text, marginBottom: 6 }}>{dialog.title}</Text>
                {dialog.message ? (
                  <Text style={{ fontSize: 14, color: colors.textMuted, lineHeight: 20, marginBottom: 16 }}>{dialog.message}</Text>
                ) : (
                  <View style={{ height: 10 }} />
                )}
                <View style={{ flexDirection: "row", gap: 10 }}>
                  <GradientButton
                    title={dialog.cancelText ?? "Cancelar"}
                    variant="ghost"
                    onPress={() => closeDialog(false)}
                    style={{ flex: 1 }}
                  />
                  <GradientButton
                    title={dialog.confirmText ?? "Aceptar"}
                    variant={dialog.destructive ? "danger" : "primary"}
                    onPress={() => closeDialog(true)}
                    style={{ flex: 1 }}
                  />
                </View>
              </Animated.View>
            </View>
          ) : null}
        </Modal>
      </ConfirmContext.Provider>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastApi {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error("useToast debe usarse dentro de un <FeedbackProvider>");
  return ctx;
}

export function useConfirm(): ConfirmFn {
  const ctx = useContext(ConfirmContext);
  if (!ctx) throw new Error("useConfirm debe usarse dentro de un <FeedbackProvider>");
  return ctx;
}

const styles = StyleSheet.create({
  toastLayer: { position: "absolute", left: 12, right: 12, gap: 8, alignItems: "center", zIndex: 1000 },
  toast: {
    width: "100%",
    maxWidth: 520,
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
    paddingVertical: 12,
    paddingHorizontal: 14,
    borderRadius: 14,
    borderLeftWidth: 4,
  },
  toastIcon: { fontSize: 16 },
  toastText: { flex: 1, fontSize: 13, lineHeight: 18, fontWeight: "500" },
  dialogRoot: { flex: 1, alignItems: "center", justifyContent: "center", padding: 24 },
  dialog: { width: "100%", maxWidth: 400, borderRadius: 20, padding: 20 },
});
