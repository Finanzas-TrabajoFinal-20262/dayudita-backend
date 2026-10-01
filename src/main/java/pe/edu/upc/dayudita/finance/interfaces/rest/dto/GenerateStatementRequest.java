package pe.edu.upc.dayudita.finance.interfaces.rest.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record GenerateStatementRequest(
        @NotNull(message = "La fecha de corte es obligatoria")
        LocalDate cutoffDate
) {
}
