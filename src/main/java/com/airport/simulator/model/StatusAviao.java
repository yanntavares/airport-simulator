package com.airport.simulator.model;

public enum StatusAviao {
    QUERENDO_DECOLAR (0),
    DECOLANDO (1),
    EM_VOO (2),
    QUERENDO_POUSAR (3),
    POUSANDO (4);

    private final int codigo;

    StatusAviao(int codigo) {
        this.codigo = codigo;
    }

    public int getCodigo() {
        return codigo;
    }

    public static StatusAviao fromCodigo(int codigo) {
        for (StatusAviao status: values()) {
            if (status.getCodigo() == codigo) {
                return status;
            }
        }
        throw new IllegalArgumentException("Código de status inválido: " + codigo);
    }
}