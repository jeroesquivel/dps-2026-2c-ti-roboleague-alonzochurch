package com.dps.roboleague.adapter.in.web.audit;

import com.dps.roboleague.domain.port.in.FindAuditTrail;
import com.dps.roboleague.domain.shared.Identifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta de la traza de auditoría de un sujeto (competencia, categoría, ronda, corrida,
 * apelación, equipo o temporada). Es de sólo lectura: los eventos los genera el núcleo al ejecutar
 * cada operación y esta API no ofrece forma de crearlos, modificarlos ni borrarlos.
 */
@RestController
@RequestMapping("/audit")
public final class AuditController {

    private final FindAuditTrail findAuditTrail;

    public AuditController(FindAuditTrail findAuditTrail) {
        this.findAuditTrail = findAuditTrail;
    }

    /**
     * Un sujeto bien formado sin eventos (o inexistente) responde {@code 200} con una lista vacía; sólo un
     * identificador ausente o de un tipo no soportado responde {@code 400}.
     */
    @GetMapping
    public AuditTrailResponse trail(@RequestParam(name = "subjectId", required = false) String subjectId) {
        Identifier subject = AuditWebMapper.toSubject(subjectId);
        return AuditWebMapper.toResponse(subject, findAuditTrail.execute(new FindAuditTrail.Command(subject)));
    }
}
