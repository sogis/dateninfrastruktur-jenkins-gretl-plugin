package ch.so.agi.jenkins.gretldatenportal;

public final class DatenportalJobResolver {
    private final DefaultGuiDefinitionFactory defaultGuiDefinitionFactory;

    public DatenportalJobResolver() {
        this(new DefaultGuiDefinitionFactory());
    }

    public DatenportalJobResolver(DefaultGuiDefinitionFactory defaultGuiDefinitionFactory) {
        this.defaultGuiDefinitionFactory = defaultGuiDefinitionFactory;
    }

    public ResolvedDatenportalJob resolve(OrganizationUnit organization, DatasetEntry dataset) {
        GuiDefinition gui = defaultGuiDefinitionFactory.create(dataset.getDefinition().isSeries());
        return new ResolvedDatenportalJob(
                organization,
                dataset,
                gui,
                organization.getNotificationConfiguration());
    }
}
