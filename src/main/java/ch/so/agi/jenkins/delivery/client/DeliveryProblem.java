package ch.so.agi.jenkins.delivery.client;

/** Only fixed safe diagnostics cross the browser/pipeline boundary. */
public final class DeliveryProblem extends RuntimeException {
    private final int status;private final String code;private final Wire.ValidationIssue issue;
    public DeliveryProblem(int status,String code){this(status,code,null);}
    private DeliveryProblem(int status,String code,Wire.ValidationIssue issue){super(message(code));this.status=status;this.code=code;this.issue=issue;}
    public Wire.ValidationIssue issue(){return issue;}
    public static DeliveryProblem validation(String code,long row,String attribute){
        if(!java.util.Set.of("HEADER","COLUMN_COUNT","CELL_LIMIT","ROW_LIMIT","QUOTE","DIALECT","UTF8","MANDATORY","INVALID_TYPE","EMPTY_SNAPSHOT","SNAPSHOT_LIMIT","MODEL_RULE","BUSINESS_KEY_DUPLICATE","XLSX_ROW_LIMIT","XLSX_CELL_LIMIT").contains(code)
            || row<0 || row>10000001 || (attribute!=null&&!attribute.matches("[A-Za-z][A-Za-z0-9_]{0,127}")))return unavailable();
        return new DeliveryProblem(409,"VALIDATION_FAILED",new Wire.ValidationIssue(code,row,attribute));
    }
    public int status(){return status;}public String code(){return code;}
    public static DeliveryProblem invalid(){return new DeliveryProblem(400,"INVALID_INPUT");}
    public static DeliveryProblem unavailable(){return new DeliveryProblem(503,"DEPENDENCY_UNAVAILABLE");}
    public static DeliveryProblem login(){return new DeliveryProblem(401,"LOGIN_REQUIRED");}
    private static String message(String code){return switch(code){
        case "LOGIN_REQUIRED","NOT_AUTHENTICATED"->"Bitte erneut über Keycloak anmelden.";
        case "PRINCIPAL_UNKNOWN"->"Bitte bestätigen Sie Ihre verifizierte Identität. Eine Lieferberechtigung wird dabei nicht vergeben.";
        case "NOT_AUTHORIZED","PRINCIPAL_INACTIVE"->"Für diese Lieferung fehlt die aktuelle Berechtigung.";
        case "INPUT_TOO_LARGE"->"Die CSV überschreitet die zulässige Uploadgrenze.";
        case "VALIDATION_FAILED"->"Der Datenvertrag hat die CSV oder den vollständigen Bestand abgewiesen.";
        case "INPUT_MISMATCH"->"Die Eingangsdatei stimmt nicht mit dem gebundenen Upload überein.";
        case "INVALID_INPUT","INVALID_UPLOAD_REFERENCE","INVALID_UPLOAD_COMMAND","INVALID_MULTIPART","MISSING_FILE_PART","INVALID_FILE_PART","INVALID_CSV_MEDIA_TYPE","MULTIPLE_FILE_PARTS","INVALID_CSV_FILENAME","INVALID_CSV_CONTENT","EMPTY_CSV"->"Die Eingabe ist ungültig. Bitte genau eine CSV auswählen.";
        case "CONFIGURATION_CHANGED","ISSUE_NOT_AVAILABLE","MODE_NOT_ALLOWED"->"Der Liefervertrag hat sich geändert. Bitte Auswahl neu laden.";
        case "INVALID_STATE","STALE_EXECUTION","IDEMPOTENCY_CONFLICT","DATASET_BUSY"->"Der Auftrag hat sich geändert. Bitte aktuellen Status anzeigen.";
        case "PROCESSOR_NOT_READY"->"Die CSV-Verarbeitung ist noch nicht freigegeben.";
        default->"Der Lieferdienst ist vorübergehend nicht verfügbar.";
    };}
}
