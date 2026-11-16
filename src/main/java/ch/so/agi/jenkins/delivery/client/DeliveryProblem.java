package ch.so.agi.jenkins.delivery.client;

/** Only fixed safe diagnostics cross the browser/pipeline boundary. */
public final class DeliveryProblem extends RuntimeException {
    private final int status;
    private final String code;
    public DeliveryProblem(int status, String code) {
        super(message(code));
        this.status = status;
        this.code = code;
    }
    public int status(){return status;}public String code(){return code;}
    public static DeliveryProblem invalid(){return new DeliveryProblem(400,"INVALID_INPUT");}
    public static DeliveryProblem unavailable(){return new DeliveryProblem(503,"DEPENDENCY_UNAVAILABLE");}
    public static DeliveryProblem login(){return new DeliveryProblem(401,"LOGIN_REQUIRED");}
    private static String message(String code) {
        return switch (code) {
            case "LOGIN_REQUIRED" -> "Bitte erneut über Keycloak anmelden.";
            case "NOT_AUTHORIZED" -> "Für diese Lieferung fehlt die aktuelle Berechtigung.";
            case "NOT_FOUND" -> "Die Lieferung wurde nicht gefunden.";
            case "INPUT_TOO_LARGE" -> "Die CSV überschreitet die zulässige Uploadgrenze.";
            case "INVALID_INPUT", "INVALID_MULTIPART", "MISSING_FILE_PART", "INVALID_FILE_PART",
                 "INVALID_CSV_MEDIA_TYPE", "MULTIPLE_FILE_PARTS", "INVALID_CSV_FILENAME",
                 "INVALID_CSV_CONTENT", "EMPTY_CSV" -> "Die Eingabe ist ungültig. Bitte genau eine CSV auswählen.";
            case "INVALID_STATE" -> "Der Auftrag hat sich geändert. Bitte den aktuellen Status anzeigen.";
            case "PROCESSOR_NOT_READY" -> "Die CSV-Verarbeitung ist vorübergehend nicht verfügbar.";
            default -> "Der Lieferdienst ist vorübergehend nicht verfügbar.";
        };
    }
}
