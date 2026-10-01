package pe.edu.upc.dayudita.finance.interfaces.rest.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdateFinancialConfigurationRequest(

        @NotNull(message = "El capital minimo es obligatorio")
        BigDecimal capitalMinimo,

        @NotNull(message = "El capital maximo es obligatorio")
        BigDecimal capitalMaximo,

        @NotNull(message = "La TEA minima es obligatoria")
        BigDecimal teaMinima,

        @NotNull(message = "La TEA maxima es obligatoria")
        BigDecimal teaMaxima,

        @NotNull(message = "El plazo maximo en meses es obligatorio")
        @Min(value = 1, message = "El plazo maximo debe ser al menos 1 mes")
        @Max(value = 4, message = "El plazo maximo no puede superar los 4 meses")
        Integer plazoMaximoMeses,

        @NotNull(message = "El maximo de periodos de gracia es obligatorio")
        @Min(value = 0, message = "El maximo de periodos de gracia no puede ser negativo")
        Integer maxPeriodosGracia,

        @NotNull(message = "Debe indicar si la tienda otorga gracia")
        Boolean otorgaGracia

) {
}
