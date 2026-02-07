package org.slb4j.ext.layouts;

import org.slb4j.Plugin;
import org.slb4j.layout.Layouts;

/**
 * SLB4J plugin that provides support for XmlLayout and YamlLayout.
 */
public class LayoutsPlugin implements Plugin {

    /**
     * Default constructor.
     */
    public LayoutsPlugin() {
        // nothing to do
    }

    @Override
    public String name() {
        return getClass().getPackageName();
    }

    @Override
    public void init() {
        Layouts.registerLayout("CsvLayout", CsvLayoutBuilder:: new);
        Layouts.registerLayout("XmlLayout", XmlLayoutBuilder:: new);
        Layouts.registerLayout("YamlLayout", YamlLayoutBuilder::new);
    }
}
