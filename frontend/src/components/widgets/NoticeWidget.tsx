import React from "react";
import Animated, { FadeInDown } from "react-native-reanimated";

import { TypedWidget } from "../../services/types";
import NoticeBox, { toNoticeKind } from "../ui/NoticeBox";

/** Aviso destacado del mundo (p. ej. "Este programa no reemplaza la valoración de un profesional"). */
export default function NoticeWidget({ widget, delay = 0 }: { widget: TypedWidget<"notice">; delay?: number }) {
  return (
    <Animated.View entering={FadeInDown.delay(delay).duration(380)}>
      <NoticeBox tone={toNoticeKind(widget.data.tone)} title={widget.title ?? undefined} body={widget.data.body} />
    </Animated.View>
  );
}
