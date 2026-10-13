package com.dps.roboleague.adapter.in.web.appeal;

import com.dps.roboleague.domain.port.in.AcceptAppeal;
import com.dps.roboleague.domain.port.in.FindAppeal;
import com.dps.roboleague.domain.port.in.RejectAppeal;
import com.dps.roboleague.domain.port.in.SubmitAppeal;
import com.dps.roboleague.domain.shared.AppealId;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Adaptador HTTP de las apelaciones. Aceptar y rechazar son operaciones distintas con su propio
 * recurso ({@code acceptance} y {@code rejection}); toda decisión pasa por los puertos de entrada.
 */
@RestController
@RequestMapping("/appeals")
public final class AppealController {

    private final SubmitAppeal submitAppeal;
    private final FindAppeal findAppeal;
    private final AcceptAppeal acceptAppeal;
    private final RejectAppeal rejectAppeal;

    public AppealController(SubmitAppeal submitAppeal, FindAppeal findAppeal, AcceptAppeal acceptAppeal,
            RejectAppeal rejectAppeal) {
        this.submitAppeal = submitAppeal;
        this.findAppeal = findAppeal;
        this.acceptAppeal = acceptAppeal;
        this.rejectAppeal = rejectAppeal;
    }

    @PostMapping
    public ResponseEntity<SubmittedAppealResponse> submit(@RequestBody SubmitAppealRequest request) {
        AppealId appealId = submitAppeal.execute(AppealWebMapper.toSubmitCommand(request));
        return ResponseEntity.created(URI.create("/appeals/" + appealId.value()))
                .body(new SubmittedAppealResponse(appealId.value()));
    }

    @GetMapping("/{appealId}")
    public AppealResponse find(@PathVariable("appealId") String appealId) {
        return AppealWebMapper.toResponse(findAppeal.execute(AppealId.of(appealId)));
    }

    @PostMapping("/{appealId}/acceptance")
    public AppealResponse accept(@PathVariable("appealId") String appealId,
            @RequestBody AcceptAppealRequest request) {
        return AppealWebMapper.toResponse(acceptAppeal.execute(AppealWebMapper.toAcceptCommand(appealId, request)));
    }

    @PostMapping("/{appealId}/rejection")
    public AppealResponse reject(@PathVariable("appealId") String appealId,
            @RequestBody RejectAppealRequest request) {
        return AppealWebMapper.toResponse(rejectAppeal.execute(AppealWebMapper.toRejectCommand(appealId, request)));
    }
}
