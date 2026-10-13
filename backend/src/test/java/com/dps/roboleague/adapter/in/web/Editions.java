package com.dps.roboleague.adapter.in.web;

import com.dps.roboleague.domain.shared.DateRange;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reparte períodos de dos días, contiguos pero sin superponerse, para las competencias de los tests de
 * API. El contexto de Spring es compartido entre clases de test y las temporadas no pueden
 * superponerse. Como cada test de API crea su propia temporada, el reparto no se agota: cuando los
 * períodos pasan de año, quien crea la temporada usa {@code period.start().getYear()} como año. Las
 * fechas empiezan en 2026 y las edades de los integrantes de los fixtures siguen dentro de la
 * categoría de 12 a 17 años durante 2026 y 2027.
 */
final class Editions {

    private static final LocalDate FIRST_DAY = LocalDate.of(2026, 3, 1);
    private static final AtomicInteger NEXT = new AtomicInteger();

    private Editions() {
    }

    static DateRange nextPeriod() {
        LocalDate start = FIRST_DAY.plusDays(2L * NEXT.getAndIncrement());
        return DateRange.of(start, start.plusDays(1));
    }
}
