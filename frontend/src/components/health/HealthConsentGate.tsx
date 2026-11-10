import { router } from "expo-router";
import React, { useState } from "react";
import { View } from "react-native";

import { useLoader } from "../../hooks/useLoader";
import { extractErrorMessage } from "../../services/api";
import { fetchAccount, setHealthDataConsent } from "../../services/profileService";
import { useTheme } from "../../theme";
import ConsentSheet, { ConsentPoint } from "../profile360/ConsentSheet";
import { useToast } from "../ui/Feedback";
import ScreenHeader from "../ui/ScreenHeader";
import { ScreenSkeleton } from "../ui/Skeleton";

export const HEALTH_CONSENT_POINTS: ConsentPoint[] = [
  {
    icon: "🩺",
    title: "Qué guardamos",
    body: "Tu chequeo de salud (síntomas, condiciones, medicamentos que afectan el ejercicio) y tu check-in diario (sueño, energía, ánimo, estrés y dolor). Solo lo que decidas responder.",
  },
  {
    icon: "🎯",
    title: "Para qué",
    body: "Únicamente para cuidarte: ajustar la intensidad, evitar ejercicios que no te convienen y avisarte cuando conviene consultar a un profesional.",
  },
  {
    icon: "🔐",
    title: "Cómo lo cuidamos",
    body: "Son datos sensibles: los guardamos cifrados, no los vendemos ni los compartimos con terceros. Darlos es opcional.",
  },
  {
    icon: "↩️",
    title: "Puedes cambiar de opinión",
    body: "Revoca la autorización cuando quieras en Cuenta › Privacidad y datos: borraremos tus chequeos, condiciones, check-ins y métricas de bienestar.",
  },
  {
    icon: "⚖️",
    title: "Tus derechos",
    body: "Ley 1581 de 2012: puedes conocer, actualizar, rectificar y suprimir tus datos, y exportarlos desde tu Cuenta.",
  },
];

/**
 * Antes del chequeo de salud y del check-in: si la cuenta no ha autorizado el tratamiento de datos
 * de salud, muestra la hoja de consentimiento (el servidor respondería 403 sin él).
 */
export default function HealthConsentGate({ title, children }: { title: string; children: React.ReactNode }) {
  const { colors } = useTheme();
  const toast = useToast();
  const account = useLoader(fetchAccount);
  const [granted, setGranted] = useState(false);
  const [saving, setSaving] = useState(false);

  if (account.loading && !account.data) return <ScreenSkeleton cards={3} />;
  // Sin datos (sin conexión) o backend sin `legal`: se sigue; el servidor decide.
  const needsConsent = account.data?.legal != null && !account.data.legal.healthDataConsent && !granted;
  if (!needsConsent) return <>{children}</>;

  function leave() {
    if (router.canGoBack()) router.back();
    else router.replace("/home");
  }

  async function accept() {
    setSaving(true);
    try {
      await setHealthDataConsent(true);
      setGranted(true);
      toast.success("Gracias por tu confianza. Cuidaremos estos datos.");
    } catch (error) {
      toast.error(extractErrorMessage(error));
    } finally {
      setSaving(false);
    }
  }

  return (
    <View style={{ flex: 1, backgroundColor: colors.bg }}>
      <ScreenHeader title={title} subtitle="Antes de empezar" onBack={leave} />
      <ConsentSheet
        areaName="Salud"
        areaIcon="🩺"
        title="Tus datos de salud son privados"
        lead="Para guardar tu chequeo y tu check-in necesitamos tu autorización expresa. Es opcional: sin ella puedes seguir entrenando en modo prudente."
        points={HEALTH_CONSENT_POINTS}
        acceptText="Autorizo, continuar"
        declineText="Ahora no"
        loading={saving}
        onAccept={accept}
        onDecline={leave}
      />
    </View>
  );
}
