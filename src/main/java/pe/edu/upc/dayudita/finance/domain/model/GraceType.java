package pe.edu.upc.dayudita.finance.domain.model;

/**
 * Tipo de cuota dentro del cronograma del método francés:
 * T = gracia total, en los primeros 0 a 3 periodos (no se paga, el interés a la TEQ se capitaliza),
 * S = cuota estándar (amortiza capital).
 */
public enum GraceType {
    TOTAL,
    STANDARD
}
