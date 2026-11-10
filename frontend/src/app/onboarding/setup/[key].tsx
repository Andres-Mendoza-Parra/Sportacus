import { router, useLocalSearchParams } from "expo-router";
import { useState } from "react";

import FormScreen from "../../../components/forms/FormScreen";
import EnrollResult from "../../../components/onboarding/EnrollResult";
import { useToast } from "../../../components/ui/Feedback";
import { useWorld } from "../../../context/WorldContext";
import { extractErrorMessage } from "../../../services/api";
import { Answers, EnrollResponse } from "../../../services/types";
import { enrollWorld, fetchWorldOnboarding } from "../../../services/worldsService";

/**
 * Inscripción en un mundo: primero las preguntas mínimas para armar el plan; las opcionales quedan
 * plegadas en "Afinar más (opcional)". Con ?mode=edit (tarjeta "Afina tu plan") vuelve atrás al guardar.
 */
export default function WorldSetupScreen() {
  const { key, mode } = useLocalSearchParams<{ key: string; mode?: string }>();
  const editing = mode === "edit";
  const toast = useToast();
  const { refresh, invalidate } = useWorld();
  const [result, setResult] = useState<EnrollResponse | null>(null);

  async function submit(answers: Answers) {
    try {
      const response = await enrollWorld(key, answers);
      await refresh().catch(() => undefined);
      if (editing) {
        invalidate();
        toast.success("Listo: ajustamos tu plan con lo que nos contaste.");
        router.dismissTo("/home");
        return;
      }
      setResult(response);
    } catch (error) {
      toast.error(extractErrorMessage(error));
      throw error;
    }
  }

  function exit() {
    if (router.canGoBack()) router.back();
    else if (editing) router.replace("/home");
    else router.replace({ pathname: "/onboarding/world/[key]", params: { key } });
  }

  if (result) return <EnrollResult result={result} onEnter={() => router.dismissTo("/home")} />;
  return (
    <FormScreen
      load={() => fetchWorldOnboarding(key)}
      loadKey={key}
      submitLabel={editing ? "Guardar" : "Crear mi plan"}
      onSubmit={submit}
      onExit={exit}
      exitIcon={editing ? "close" : "back"}
      deferOptional={!editing}
    />
  );
}
