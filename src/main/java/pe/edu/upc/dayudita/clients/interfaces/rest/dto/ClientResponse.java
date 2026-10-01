package pe.edu.upc.dayudita.clients.interfaces.rest.dto;

import java.math.BigDecimal;

public record ClientResponse(

        Long id,
        Long clientAccountId,
        String firstName,
        String lastName,
        String documentNumber,
        String email,
        String phone,
        BigDecimal teaPactada,
        BigDecimal teaMoratoriaPactada,
        Boolean active

) {
}
