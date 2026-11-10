import { router } from "expo-router";

import FormScreen from "../../components/forms/FormScreen";
import { useToast } from "../../components/ui/Feedback";
import { useWorld } from "../../context/WorldContext";
import { extractErrorMessage } from "../../services/api";
import { fetchProfileForm, saveProfileForm } from "../../services/profileService";
import { Answers } from "../../services/types";

/** Editar los datos básicos del perfil común (desde Cuenta). Todo es opcional y se guarda parcial. */
export default function ProfileStep() {
  const toast = useToast();
  const { invalidate } = useWorld();

  async function submit(answers: Answers) {
    try {
      await saveProfileForm(answers);
      invalidate();
    } catch (error) {
      toast.error(extractErrorMessage(error));
      throw error;
    }
    toast.success("Perfil actualizado. Tus planes usarán estos datos.");
    exit();
  }

  function exit() {
    if (router.canGoBack()) router.back();
    else router.replace("/account");
  }

  return (
    <FormScreen
      load={fetchProfileForm}
      submitLabel="Guardar"
      onSubmit={submit}
      onExit={exit}
      exitIcon="close"
      optional
    />
  );
}
