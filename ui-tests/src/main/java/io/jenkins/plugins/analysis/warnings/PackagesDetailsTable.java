package io.jenkins.plugins.analysis.warnings;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

public class PackagesDetailsTable extends AbstractDetailsTable {
    /**
     * Creates a PackagesDetailsTable
     */
    public PackagesDetailsTable(final WebElement tab, final AnalysisResult resultDetailsPage) {
        super(tab, "packageName", resultDetailsPage);
        this.updateTableRows();
    }
}
