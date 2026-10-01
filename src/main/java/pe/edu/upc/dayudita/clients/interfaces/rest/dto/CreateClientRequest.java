package pe.edu.upc.dayudita.clients.interfaces.rest.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record CreateClientRequest(

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
        String firstName,

        @NotBlank(message = "El apellido es obligatorio")
        @Size(max = 100, message = "El apellido no puede superar los 100 caracteres")
        String lastName,

        @NotBlank(message = "El documento es obligatorio")
        @Size(max = 20, message = "El documento no puede superar los 20 caracteres")
        String documentNumber,

        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo no tiene un formato valido")
        String email,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres")
        String password,

        @Size(max = 20, message = "El telefono no puede superar los 20 caracteres")
        String phone,

        @NotNull(message = "La TEA pactada es obligatoria")
        @DecimalMin(value = "0.0000001", message = "La TEA pactada debe ser mayor a 0")
        BigDecimal teaPactada,

        @NotNull(message = "La TEA moratoria pactada es obligatoria")
        @DecimalMin(value = "0.0000001", message = "La TEA moratoria pactada debe ser mayor a 0")
        BigDecimal teaMoratoriaPactada

) {
}
