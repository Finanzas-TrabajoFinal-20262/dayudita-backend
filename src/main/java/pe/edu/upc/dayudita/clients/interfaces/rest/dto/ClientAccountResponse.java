package pe.edu.upc.dayudita.clients.interfaces.rest.dto;

import java.math.BigDecimal;

public record ClientAccountResponse(
        Long clientAccountId,
        Long storeId,
        String storeName,
        BigDecimal teaPactada,
        BigDecimal teaMoratoriaPactada,
        Boolean active
) {
}
