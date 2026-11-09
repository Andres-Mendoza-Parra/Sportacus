import { router, useLocalSearchParams } from "expo-router";
import { useState } from "react";

import FormScreen from "../../../components/forms/FormScreen";
import EnrollResult from "../../../components/onboarding/EnrollResult";
import { useToast } from "../../../components/ui/Feedback";
import { useAuth } from "../../../context/AuthContext";
import { useWorld } from "../../../context/WorldContext";
import { extractErrorMessage } from "../../../services/api";
import { Answers, EnrollResponse } from "../../../services/types";
import { enrollWorld, fetchWorldOnboarding } from "../../../services/worldsService";

/** Paso 4–5: onboarding propio del mundo → inscripción (crea el plan y activa el mundo) → resultado. */
export default function WorldSetupScreen() {
  const { key } = useLocalSearchParams<{ key: string }>();
  const toast = useToast();
  const { markOnboarded } = useAuth();
  const { refresh } = useWorld();
  const [result, setResult] = useState<EnrollResponse | null>(null);

  async function submit(answers: Answers) {
    try {
      const response = await enrollWorld(key, answers);
      await markOnboarded();
      await refresh().catch(() => undefined);
      setResult(response);
    } catch (error) {
      toast.error(extractErrorMessage(error));
      throw error;
    }
  }

  function exit() {
    if (router.canGoBack()) router.back();
    else router.replace({ pathname: "/onboarding/world/[key]", params: { key } });
  }

  if (result) return <EnrollResult result={result} onEnter={() => router.replace("/home")} />;
  return <FormScreen load={() => fetchWorldOnboarding(key)} loadKey={key} submitLabel="Crear mi plan" onSubmit={submit} onExit={exit} />;
}
