package ch.so.agi.jenkins.gretldatenportal;

import java.io.IOException;
import java.nio.file.Path;

public final class DatasetGuiParser {
    private final GuiDefinitionParser guiDefinitionParser;

    public DatasetGuiParser() {
        this(new GuiDefinitionParser());
    }

    public DatasetGuiParser(GuiDefinitionParser guiDefinitionParser) {
        this.guiDefinitionParser = guiDefinitionParser;
    }

    public GuiDefinition parse(Path yamlFile) throws IOException {
        return guiDefinitionParser.parse(yamlFile);
    }
}
