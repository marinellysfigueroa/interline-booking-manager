package com.insightdevelop.interline.application.booking;

import com.insightdevelop.interline.domain.booking.PaymentMethod;
import com.insightdevelop.interline.domain.shared.Miles;
import com.insightdevelop.interline.domain.shared.Money;
import java.util.Objects;

/**
 * @param cash         {@code null} si {@code method == MILES}
 * @param miles        {@link Miles#ZERO} si {@code method == CASH}
 * @param memberNumber {@code null} si no se usan millas
 */
public record PaymentCommand(PaymentMethod method, Money cash, Miles miles, String memberNumber) {

    public PaymentCommand {
        Objects.requireNonNull(method, "method");
        miles = miles == null ? Miles.ZERO : miles;
    }
}
