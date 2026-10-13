package com.dps.roboleague.adapter.in.web.round;

import com.dps.roboleague.domain.port.in.FindRound;
import com.dps.roboleague.domain.port.in.ScheduleRound;
import com.dps.roboleague.domain.shared.RoundId;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rounds")
public final class RoundController {

    private final ScheduleRound scheduleRound;
    private final FindRound findRound;

    public RoundController(ScheduleRound scheduleRound, FindRound findRound) {
        this.scheduleRound = scheduleRound;
        this.findRound = findRound;
    }

    @PostMapping
    public ResponseEntity<ScheduledRoundResponse> schedule(@RequestBody ScheduleRoundRequest request) {
        RoundId roundId = scheduleRound.execute(RoundWebMapper.toCommand(request));
        return ResponseEntity.created(URI.create("/rounds/" + roundId.value()))
                .body(RoundWebMapper.toResponse(roundId));
    }

    @GetMapping("/{roundId}")
    public RoundResponse find(@PathVariable("roundId") String roundId) {
        return RoundWebMapper.toResponse(findRound.execute(RoundId.of(roundId)));
    }
}
