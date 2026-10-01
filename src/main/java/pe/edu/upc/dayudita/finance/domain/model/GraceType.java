package pe.edu.upc.dayudita.finance.domain.model;

/**
 * Tipo de cuota dentro del cronograma del método francés con gracia:
 * T = gracia total (no se paga, el interés se capitaliza),
 * P = gracia parcial (solo se paga el interés),
 * S = cuota estándar (amortiza capital).
 */
public enum GraceType {
    TOTAL,
    PARTIAL,
    STANDARD
}
