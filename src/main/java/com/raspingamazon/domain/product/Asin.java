package com.raspingamazon.domain.product;

import java.util.Objects;

public record Asin(String value) {

    public Asin {
        Objects.requireNonNull(value, "ASIN must not be null");

        if (value.isBlank()) {
            throw new IllegalArgumentException("ASIN must not be blank");
        }

        if (value.length() > 10) {
            throw new IllegalArgumentException("ASIN must not exceed 10 characters");
        }
    }
}