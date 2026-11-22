package pe.edu.upc.dayudita.finance.application;

import java.math.BigDecimal;

public record CreditTerms(
        BigDecimal teaPactada,
        BigDecimal teaMoratoriaPactada,
        int numeroMeses,
        int periodosGracia
) {
}
