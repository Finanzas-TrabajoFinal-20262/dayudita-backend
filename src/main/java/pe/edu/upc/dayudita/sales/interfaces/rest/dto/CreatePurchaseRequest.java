package pe.edu.upc.dayudita.sales.interfaces.rest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import pe.edu.upc.dayudita.sales.domain.model.PurchasePaymentMode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CreatePurchaseRequest(

        @NotNull(message = "El cliente es obligatorio")
        Long clientId,

        @NotNull(message = "La fecha de compra es obligatoria")
        LocalDate purchaseDate,

        @NotNull(message = "La modalidad de pago es obligatoria")
        PurchasePaymentMode paymentMode,

        Integer numeroPeriodos,

        BigDecimal teaPactada,

        BigDecimal teaMoratoriaPactada,

        @Min(value = 0, message = "Los periodos de gracia deben estar entre 0 y 3")
        @Max(value = 3, message = "Los periodos de gracia deben estar entre 0 y 3")
        Integer periodosGracia,

        @NotEmpty(message = "La compra debe tener al menos un producto")
        List<@Valid PurchaseItemRequest> items

) {
}
