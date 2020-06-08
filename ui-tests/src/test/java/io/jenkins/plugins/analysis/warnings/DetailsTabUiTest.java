package io.jenkins.plugins.analysis.warnings;

import java.util.Collection;
import java.util.List;

import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

import org.jenkinsci.test.acceptance.junit.AbstractJUnitTest;
import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.jenkinsci.test.acceptance.po.Build;
import org.jenkinsci.test.acceptance.po.FreeStyleJob;

import io.jenkins.plugins.analysis.warnings.AnalysisResult.Tab;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for the details tab part of issue overview page.
 *
 * @author Nils Engelbrecht
 * @author Kevin Richter
 * @author Simon Schönwiese
 */
@WithPlugins("warnings-ng")
public class DetailsTabUiTest extends AbstractJUnitTest {
    private static final String WARNINGS_PLUGIN_PREFIX = "/details_tab_test/";

    /**
     * When a single warning is being recognized only the issues-tab should be shown.
     */
    @Test
    public void shouldPopulateDetailsTabSingleWarning() {
        FreeStyleJob job = createFreeStyleJob("java1Warning.txt");
        job.addPublisher(IssuesRecorder.class, recorder -> recorder.setToolWithPattern("Java", "**/*.txt"));
        job.save();

        Build build = job.startBuild().waitUntilFinished();
        assertThat(build.isSuccess()).isTrue();

        AnalysisResult resultPage = new AnalysisResult(build, "java");
        resultPage.open();

        Collection<Tab> tabs = resultPage.getAvailableTabs();
        assertThat(tabs).containsOnlyOnce(Tab.ISSUES);
        assertThat(resultPage.getActiveTab()).isEqualTo(Tab.ISSUES);

        IssuesDetailsTable issuesDetailsTable = resultPage.openIssuesTable();
        assertThat(issuesDetailsTable.getTableRows()).hasSize(1);
    }

    /**
     * When two warnings are being recognized in one file the tabs issues, files and folders should be shown.
     */
    @Test
    public void shouldPopulateDetailsTabMultipleWarnings() {
        FreeStyleJob job = createFreeStyleJob("java2Warnings.txt");
        job.addPublisher(IssuesRecorder.class, recorder -> recorder.setToolWithPattern("Java", "**/*.txt"));
        job.save();

        Build build = job.startBuild().waitUntilFinished();
        assertThat(build.isSuccess()).isTrue();

        AnalysisResult resultPage = new AnalysisResult(build, "java");
        resultPage.open();

        assertThat(resultPage).hasOnlyAvailableTabs(Tab.FOLDERS, Tab.FILES, Tab.ISSUES);

        PropertyDetailsTable foldersDetailsTable = resultPage.openPropertiesTable(Tab.FOLDERS);
        assertThat(foldersDetailsTable).hasTotal(2);

        PropertyDetailsTable filesDetailsTable = resultPage.openPropertiesTable(Tab.FILES);
        assertThat(filesDetailsTable).hasTotal(2);

        IssuesDetailsTable issuesDetailsTable = resultPage.openIssuesTable();
        assertThat(issuesDetailsTable).hasTotal(2);
    }

    /**
     * When switching details-tab and the page is being reloaded, the previously selected tab should be memorized and
     * still be active.
     */
    @Test
    public void shouldMemorizeSelectedTabAsActiveOnPageReload() {
        FreeStyleJob job = createFreeStyleJob("../checkstyle-result.xml");
        job.addPublisher(IssuesRecorder.class, recorder -> recorder.setTool("CheckStyle"));
        job.save();

        Build build = job.startBuild().waitUntilFinished();
        assertThat(build.isSuccess()).isTrue();

        AnalysisResult resultPage = new AnalysisResult(build, "checkstyle");
        resultPage.open();

        assertThat(resultPage).hasOnlyAvailableTabs(Tab.ISSUES, Tab.TYPES, Tab.CATEGORIES);

        assertThat(resultPage.getActiveTab()).isNotEqualTo(Tab.TYPES);
        resultPage.openTab(Tab.TYPES);
        assertThat(resultPage.getActiveTab()).isEqualTo(Tab.TYPES);

        resultPage.reload();
        assertThat(resultPage.getActiveTab()).isEqualTo(Tab.TYPES);
    }

    /**
     * When having a larger checkstyle result, the table should display all Tabs, tables and pages correctly and should
     * be able to change the page.
     */
    @Test
    public void shouldWorkWithMultipleTabsAndPages() {
        FreeStyleJob job = createFreeStyleJob("../checkstyle-result.xml");
        job.addPublisher(IssuesRecorder.class, recorder -> recorder.setTool("CheckStyle"));
        job.save();

        Build build = job.startBuild().waitUntilFinished();
        assertThat(build.isSuccess()).isTrue();

        AnalysisResult resultPage = new AnalysisResult(build, "checkstyle");
        resultPage.open();

        assertThat(resultPage).hasOnlyAvailableTabs(Tab.ISSUES, Tab.TYPES, Tab.CATEGORIES);

        PropertyDetailsTable categoriesDetailsTable = resultPage.openPropertiesTable(Tab.CATEGORIES);
        assertThat(categoriesDetailsTable).hasHeaders("Category", "Total", "Distribution");
        assertThat(categoriesDetailsTable).hasSize(5).hasTotal(5);

        PropertyDetailsTable typesDetailsTable = resultPage.openPropertiesTable(Tab.TYPES);
        assertThat(typesDetailsTable).hasHeaders("Type", "Total", "Distribution");
        assertThat(typesDetailsTable).hasSize(7).hasTotal(7);

        IssuesDetailsTable issuesDetailsTable = resultPage.openIssuesTable();
        // TODO: FIX AND UNCOMMENT
//        assertThat(issuesDetailsTable).hasColumnHeaders(Header.DETAILS, Header.FILE, Header.CATEGORY,
//            Header.TYPE, Header.SEVERITY, Header.AGE);
        assertThat(issuesDetailsTable).hasSize(10).hasTotal(11);

        List<GenericTableRow> tableRowListIssues = issuesDetailsTable.getTableRows();
        IssuesTableRow firstRow = (IssuesTableRow) tableRowListIssues.get(0);
        firstRow.toggleDetailsRow();

        issuesDetailsTable.openTablePage(2);
        assertThat(issuesDetailsTable.getSize()).isEqualTo(1);

        tableRowListIssues = issuesDetailsTable.getTableRows();
        IssuesTableRow lastIssueTableRow = (IssuesTableRow) tableRowListIssues.get(0);
        assertThat(lastIssueTableRow.getSeverity()).isEqualTo("Error");
        AnalysisResult analysisResult = lastIssueTableRow.clickOnSeverityLink();
        IssuesDetailsTable errorIssuesDetailsTable = analysisResult.openIssuesTable();
        assertThat(errorIssuesDetailsTable.getSize()).isEqualTo(6);
        for (int i = 0; i < errorIssuesDetailsTable.getSize(); i++) {
            IssuesTableRow row = (IssuesTableRow) errorIssuesDetailsTable.getTableRows().get(i);
            assertThat(row.getSeverity()).isEqualTo("Error");
        }
    }

    /**
     * When switching details-tab and the page is being reloaded, the previously selected tab should be memorized and
     * still be active.
     */
    @Test
    public void shouldAssertThatFirstRowHasNormalSeverityAndAgeOfOne() {
        FreeStyleJob job = createFreeStyleJob("cpd1Warning.xml");
        job.addPublisher(IssuesRecorder.class, recorder -> recorder.setToolWithPattern("CPD", "**/*.xml"));
        job.save();

        Build build = job.startBuild().waitUntilFinished();
        build.open();

        AnalysisSummary cpd = new AnalysisSummary(build, "cpd");

        AnalysisResult cpdDetails = cpd.openOverallResult();

        //IssuesDetailsTable issuesDetailsTable = cpdDetails.openIssuesTable();

        PropertyDetailsTable issuesDetailsTable = cpdDetails.openPropertiesTable(Tab.ISSUES);
        //assertThat(lastIssueTableRow.getSeverity()).isEqualTo("Error");

        //DetailsTableRow issuesTableFirstRow = issuesDetailsTable.getRowAs(0, DetailsTableRow.class);

//        assertThat(issuesTableFirstRow.getSeverity()).isEqualTo("Normal");
//        assertThat(issuesTableFirstRow.getAge()).isEqualTo(1);
    }

    @Test
    public void shouldShowTheCorrectAmountOfRowsSelected() {
        FreeStyleJob job = createFreeStyleJob("findbugs-severities.xml");
        job.addPublisher(IssuesRecorder.class, recorder -> recorder.setToolWithPattern("FindBugs", "**/*.xml"));
        job.save();

        Build build = job.startBuild().waitUntilFinished();
        build.open();

        AnalysisSummary resultPage = new AnalysisSummary(build, "findbugs");
        Assertions.assertThat(resultPage).isDisplayed();
        AnalysisResult findBugsDetails = resultPage.openOverallResult();

        assertThat(findBugsDetails).hasAvailableTabs(Tab.ISSUES);

        PropertyDetailsTable issuesDetailsTable = findBugsDetails.openPropertiesTable(Tab.ISSUES);

        WebElement selectDisplayLengthDiv = resultPage.getElement(By.id("issues_length"));
        Select selectDisplayLength = new Select(selectDisplayLengthDiv.findElement(By.cssSelector("label > select")));
        WebElement showingRows = resultPage.getElement(By.id("issues_info"));

        selectDisplayLength.selectByValue("10");
        WebDriverWait wait = new WebDriverWait(driver, 4, 100);
        wait.until(ExpectedConditions.textToBePresentInElement(showingRows, "Showing 1 to 10 of 12 entries"));

//        assertThat(showingRows.getText()).isEqualTo("Showing 1 to 10 of 12 entries");
//        assertThat(issuesDetailsTable.getTableRows()).hasSize(10);
        selectDisplayLength.selectByValue("25");
        wait = new WebDriverWait(driver, 4, 100);
        wait.until(ExpectedConditions.textToBePresentInElement(showingRows, "Showing 1 to 12 of 12 entries"));

        resultPage.open();
        showingRows = resultPage.getElement(By.id("issues_info"));
        wait = new WebDriverWait(driver, 4, 100);
        wait.until(ExpectedConditions.textToBePresentInElement(showingRows, "Showing 1 to 12 of 12 entries"));
    }

    @Test
    public void shouldDisplayOnlySearchedRows() {
        FreeStyleJob job = createFreeStyleJob("findbugs-severities.xml");
        job.addPublisher(IssuesRecorder.class, recorder -> recorder.setToolWithPattern("FindBugs", "**/*.xml"));
        job.save();

        Build build = job.startBuild().waitUntilFinished();
        build.open();

        AnalysisSummary resultPage = new AnalysisSummary(build, "findbugs");
        Assertions.assertThat(resultPage).isDisplayed();
        AnalysisResult findBugsDetails = resultPage.openOverallResult();

        assertThat(findBugsDetails).hasAvailableTabs(Tab.ISSUES);

        PropertyDetailsTable issuesDetailsTable = findBugsDetails.openPropertiesTable(Tab.ISSUES);

        WebElement searchDiv = resultPage.getElement(By.id("issues_filter"));
        WebElement showingRows = resultPage.getElement(By.id("issues_info"));
        WebElement searchInputField = searchDiv.findElement(By.cssSelector("label > input"));

        searchInputField.sendKeys("CalculateFrame");

        WebDriverWait wait = new WebDriverWait(driver, 4, 100);
        wait.until(ExpectedConditions.textToBePresentInElement(showingRows,
                "Showing 5 to 2 of 2 entries (filtered from 12 total entries)"));
        searchInputField.clear();

        searchInputField.sendKeys("STYLE");
        wait = new WebDriverWait(driver, 4, 100);
        wait.until(ExpectedConditions.textToBePresentInElement(showingRows,
                "Showing 1 to 7 of 7 entries (filtered from 12 total entries)"));
    }

    private FreeStyleJob createFreeStyleJob(final String... resourcesToCopy) {
        FreeStyleJob job = jenkins.getJobs().create(FreeStyleJob.class);
        ScrollerUtil.hideScrollerTabBar(driver);
        for (String resource : resourcesToCopy) {
            job.copyResource(WARNINGS_PLUGIN_PREFIX + resource);
        }
        return job;
    }
}