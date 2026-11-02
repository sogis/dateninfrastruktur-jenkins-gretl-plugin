package ch.so.agi.jenkins.gretldatenportal;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class JobDefinitionValidator {
    public List<ValidationMessage> validateOrganization(OrganizationUnit organization) {
        List<ValidationMessage> messages = new ArrayList<>();
        JobDefinition definition = organization.getJobDefinition();
        Path path = organization.getPath();

        if (definition.getJobName().isBlank()) {
            messages.add(error("Job name must not be blank.", path));
        }
        if (definition.getGradleTask().isBlank()) {
            messages.add(error("Gradle task must not be blank.", path));
        }
        return messages;
    }

    public List<ValidationMessage> validatePermissions(PermissionConfiguration permissionConfiguration, Path path) {
        List<ValidationMessage> messages = new ArrayList<>();
        if (permissionConfiguration == null || !permissionConfiguration.hasReadRestrictions()) {
            messages.add(error("permissions.read must contain at least one team.", path));
        }
        if (permissionConfiguration == null || !permissionConfiguration.hasBuildRestrictions()) {
            messages.add(error("permissions.build must contain at least one team.", path));
        }
        return messages;
    }

    public List<ValidationMessage> validateGui(GuiDefinition guiDefinition) {
        List<ValidationMessage> messages = new ArrayList<>();
        for (GuiFieldDefinition field : guiDefinition.getFields()) {
            if (field.getId().isBlank()) {
                messages.add(error("GUI field id must not be blank.", null));
            }
            if (field.getType() == ParameterType.CHOICE
                    && field.getValues().isEmpty()
                    && (field.getSource() == null || !field.getSource().isDatasets())) {
                messages.add(error("Choice field '" + field.getId() + "' needs values or source datasets.", null));
            }
            if (field.getType() == ParameterType.FILE && field.getMaxSizeMb() != null && field.getMaxSizeMb() <= 0) {
                messages.add(error("File field '" + field.getId() + "' needs a positive maxSizeMb.", null));
            }
        }
        return messages;
    }

    private ValidationMessage error(String message, Path path) {
        return new ValidationMessage(ValidationMessage.Severity.ERROR, message, path);
    }
}
