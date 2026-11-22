package pe.edu.upc.dayudita.sales.interfaces.rest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record UpdateCreditRequest(

        @NotNull(message = "La fecha de compra es obligatoria")
        LocalDate purchaseDate,

        @NotNull(message = "La TEA del credito es obligatoria")
        BigDecimal teaPactada,

        @NotNull(message = "La TEA moratoria del credito es obligatoria")
        BigDecimal teaMoratoriaPactada,

        @NotNull(message = "Debe indicar el numero de meses")
        Integer numeroMeses,

        @NotNull(message = "Debe indicar los periodos de gracia")
        @Min(value = 0, message = "Los periodos de gracia deben estar entre 0 y 3")
        @Max(value = 3, message = "Los periodos de gracia deben estar entre 0 y 3")
        Integer periodosGracia,

        @NotEmpty(message = "La compra debe tener al menos un producto")
        List<@Valid PurchaseItemRequest> items

) {
}
