import { router, useLocalSearchParams } from "expo-router";

import FormScreen from "../../components/forms/FormScreen";
import { useToast } from "../../components/ui/Feedback";
import { parseMode } from "../../navigation/onboarding";
import { extractErrorMessage } from "../../services/api";
import { fetchProfileForm, saveProfileForm } from "../../services/profileService";
import { Answers } from "../../services/types";

/** Paso 1 del recibimiento (o edición desde Cuenta con ?mode=edit): perfil común. */
export default function ProfileStep() {
  const { mode: rawMode } = useLocalSearchParams<{ mode?: string }>();
  const mode = parseMode(rawMode);
  const toast = useToast();

  async function submit(answers: Answers) {
    try {
      await saveProfileForm(answers);
    } catch (error) {
      toast.error(extractErrorMessage(error));
      throw error;
    }
    if (mode === "edit") {
      toast.success("Perfil actualizado. Tus planes usarán estos datos.");
      if (router.canGoBack()) router.back();
      else router.replace("/account");
      return;
    }
    router.replace("/onboarding/health");
  }

  function exit() {
    if (router.canGoBack()) router.back();
    else router.replace(mode === "edit" ? "/account" : "/onboarding");
  }

  return (
    <FormScreen
      load={fetchProfileForm}
      submitLabel={mode === "edit" ? "Guardar" : "Continuar"}
      onSubmit={submit}
      onExit={exit}
      exitIcon={mode === "edit" ? "close" : "back"}
    />
  );
}
