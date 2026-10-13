package com.dps.roboleague.adapter.in.web.event;

/** Contrato de entrada de la publicación de una nueva versión del reglamento. */
public record PublishRulebookRequest(RulebookRequest rulebook, String actor) {
}
