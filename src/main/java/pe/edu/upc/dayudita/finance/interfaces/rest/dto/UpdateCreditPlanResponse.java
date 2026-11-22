package pe.edu.upc.dayudita.finance.interfaces.rest.dto;

import java.math.BigDecimal;

public record UpdateCreditPlanResponse(
        CreditPlanResponse plan,
        BigDecimal montoPagadoPrevio,
        Integer pagosPrevios
) {
}
