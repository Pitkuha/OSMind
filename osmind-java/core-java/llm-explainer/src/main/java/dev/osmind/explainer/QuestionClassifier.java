package dev.osmind.explainer;

import java.util.Locale;

final class QuestionClassifier {
    private QuestionClassifier() {
    }

    static QuestionIntent classify(String rawQuestion) {
        String question = normalize(rawQuestion);
        if (containsAny(question, "нагре", "горяч", "перегрев", "температур", "heat", "hot", "overheat", "fan", "thermal")) {
            return QuestionIntent.HEAT;
        }
        if (containsAny(question, "сеть", "сетев", "трафик", "traffic", "network", "tcp", "connection", "соедин")) {
            return QuestionIntent.NETWORK;
        }
        if (containsAny(question, "файл", "шифр", "ransom", "write", "delete", "unlink", "chmod", "encrypt")) {
            return QuestionIntent.FILE_ACTIVITY;
        }
        if (containsAny(question, "скач", "curl", "wget", "tmp", "dropper", "payload", "download")) {
            return QuestionIntent.DROPPER;
        }
        if (containsAny(question, "процесс", "process", "cpu", "memory", "ram", "памят", "завис", "slow", "медлен")) {
            return QuestionIntent.PROCESS_HEALTH;
        }
        if (containsAny(question, "malware", "virus", "вирус", "опас", "suspicious", "подозр", "attack", "атака")) {
            return QuestionIntent.SECURITY;
        }
        return QuestionIntent.UNKNOWN;
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String rawQuestion) {
        return rawQuestion == null ? "" : rawQuestion.toLowerCase(Locale.ROOT).trim();
    }
}
