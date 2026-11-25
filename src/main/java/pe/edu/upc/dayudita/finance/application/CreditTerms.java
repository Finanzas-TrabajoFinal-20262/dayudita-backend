package pe.edu.upc.dayudita.finance.application;

import java.math.BigDecimal;

public record CreditTerms(
        BigDecimal teaPactada,
        BigDecimal teaMoratoriaPactada,
        int numeroPeriodos,
        int periodosGracia
) {
}
