package com.leadsyncpro.billing.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;

public final class MoneyUtil {

    private static final Locale DEFAULT_LOCALE = new Locale("tr", "TR");
    private static final Currency DEFAULT_CURRENCY = Currency.getInstance("TRY");

    private MoneyUtil() {}

    public static long roundHalfUp(BigDecimal amount) {
        return amount.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    public static String format(long amountInMinor, Currency currency) {
        Currency effectiveCurrency = currency != null ? currency : DEFAULT_CURRENCY;
        NumberFormat format = NumberFormat.getCurrencyInstance(DEFAULT_LOCALE);
        format.setCurrency(effectiveCurrency);
        return format.format(amountInMinor / Math.pow(10, effectiveCurrency.getDefaultFractionDigits()));
    }

    public static Currency defaultCurrency() {
        return DEFAULT_CURRENCY;
    }
}
