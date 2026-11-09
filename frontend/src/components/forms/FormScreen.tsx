import React from "react";
import { View } from "react-native";

import { useLoader } from "../../hooks/useLoader";
import { FormSpec } from "../../services/types";
import { useTheme } from "../../theme";
import { ScreenSkeleton } from "../ui/Skeleton";
import { ErrorState } from "../ui/States";
import FormWizard, { FormWizardProps } from "./FormWizard";

interface Props extends Omit<FormWizardProps, "spec"> {
  /** Carga el FormSpec del backend (perfil, salud, descubrimiento, mundo…). */
  load: () => Promise<FormSpec>;
  /** Cambia para volver a cargar (p. ej. la clave del mundo). */
  loadKey?: string;
}

/** Carga un formulario y lo muestra en el asistente, con esqueleto y error con reintento. */
export default function FormScreen({ load, loadKey, ...wizard }: Props) {
  const { colors } = useTheme();
  const form = useLoader(load, [loadKey]);

  if (form.loading) return <ScreenSkeleton cards={4} />;
  if (!form.data) {
    return (
      <View style={{ flex: 1, justifyContent: "center", backgroundColor: colors.bg }}>
        <ErrorState message={form.error ?? "Formulario no disponible."} onRetry={form.reload} />
      </View>
    );
  }
  return <FormWizard key={form.data.key} spec={form.data} {...wizard} />;
}
