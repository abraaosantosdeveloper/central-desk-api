package br.com.contabilizape.centraldesk.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class CpfValidator implements ConstraintValidator<Cpf, String> {

    private static final Pattern ELEVEN_DIGITS = Pattern.compile("\\d{11}");

    @Override
    public boolean isValid(String cpf, ConstraintValidatorContext context) {
        if (cpf == null) {
            return true;
        }
        if (!ELEVEN_DIGITS.matcher(cpf).matches() || hasAllDigitsEqual(cpf)) {
            return false;
        }
        return checkDigit(cpf, 9) == digitAt(cpf, 9)
                && checkDigit(cpf, 10) == digitAt(cpf, 10);
    }

    // Sequências como 11111111111 passam no cálculo, mas não são CPFs emitidos
    private static boolean hasAllDigitsEqual(String cpf) {
        return cpf.chars().distinct().count() == 1;
    }

    // Módulo 11: pesos decrescentes de (length + 1) até 2 sobre os primeiros `length` dígitos
    private static int checkDigit(String cpf, int length) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += digitAt(cpf, i) * (length + 1 - i);
        }
        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }

    private static int digitAt(String cpf, int index) {
        return cpf.charAt(index) - '0';
    }
}
