import React from "react";
import { Text } from "react-native";

import { AnswerValue, Question } from "../../services/types";
import { useTheme } from "../../theme";
import { MultiInput, OptionsInput } from "./ChoiceInputs";
import { rangeDefault } from "./formLogic";
import { NumberInput, RangeInput, TextAnswer } from "./ValueInputs";

interface Props {
  question: Question;
  value: AnswerValue | undefined;
  onChange: (value: AnswerValue | undefined) => void;
  /** Alterna una opción multi (el padre aplica max y "ninguno"). */
  onToggle: (id: string) => void;
}

/** Elige el control según `question.type`. Un tipo desconocido muestra un aviso y se puede omitir. */
export default function QuestionInput({ question, value, onChange, onToggle }: Props) {
  const { colors } = useTheme();
  const options = question.options ?? [];

  switch (question.type) {
    case "options":
      return <OptionsInput options={options} value={typeof value === "string" ? value : undefined} onChange={onChange} />;
    case "multi":
      return <MultiInput options={options} value={Array.isArray(value) ? value : []} max={question.max} onToggle={onToggle} />;
    case "range": {
      const step = question.step && question.step > 0 ? question.step : 1;
      return (
        <RangeInput
          min={question.min ?? 0}
          max={question.max ?? 100}
          step={step}
          unit={question.unit ?? ""}
          value={typeof value === "number" ? value : rangeDefault(question)}
          touched={typeof value === "number"}
          onChange={onChange}
        />
      );
    }
    case "number":
      return (
        <NumberInput
          key={question.id}
          value={typeof value === "number" ? value : undefined}
          unit={question.unit}
          min={question.min}
          max={question.max}
          onChange={onChange}
        />
      );
    case "text":
      return <TextAnswer value={typeof value === "string" ? value : ""} maxLength={question.max ?? 500} onChange={onChange} />;
    default:
      return (
        <Text style={{ fontSize: 13, color: colors.textMuted, lineHeight: 19 }}>
          Esta pregunta aún no se puede mostrar en esta versión de la app. Puedes omitirla.
        </Text>
      );
  }
}
