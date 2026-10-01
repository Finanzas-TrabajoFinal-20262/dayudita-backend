package pe.edu.upc.dayudita.clients.interfaces.rest.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AssociateClientRequest(

        @NotNull(message = "La TEA pactada es obligatoria")
        @DecimalMin(value = "0.0000001", message = "La TEA pactada debe ser mayor a 0")
        BigDecimal teaPactada,

        @NotNull(message = "La TEA moratoria pactada es obligatoria")
        @DecimalMin(value = "0.0000001", message = "La TEA moratoria pactada debe ser mayor a 0")
        BigDecimal teaMoratoriaPactada

) {
}
