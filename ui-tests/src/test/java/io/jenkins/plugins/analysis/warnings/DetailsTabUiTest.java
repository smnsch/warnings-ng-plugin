package io.jenkins.plugins.analysis.warnings;

import java.util.Collection;
import java.util.List;
import javax.print.attribute.standard.Severity;

import org.junit.Test;

import org.jenkinsci.test.acceptance.junit.AbstractJUnitTest;
import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.jenkinsci.test.acceptance.po.Build;
import org.jenkinsci.test.acceptance.po.FreeStyleJob;

import io.jenkins.plugins.analysis.warnings.AnalysisResult.Tab;
import io.jenkins.plugins.analysis.warnings.CategoriesDetailsTable.Header;
import io.jenkins.plugins.analysis.warnings.AnalysisSummary.InfoType;

import static io.jenkins.plugins.analysis.warnings.Assertions.*;

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

        FoldersDetailsTable foldersDetailsTable = resultPage.openFoldersTable();
        assertThat(foldersDetailsTable.getTotal()).isEqualTo(2);

        FilesDetailsTable filesDetailsTable = resultPage.openFilesTable();
        assertThat(filesDetailsTable.getTotal()).isEqualTo(2);

        IssuesDetailsTable issuesDetailsTable = resultPage.openIssuesTable();
        assertThat(issuesDetailsTable.getTotal()).isEqualTo(2);
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
//    recordIssues tools: [java(), javaDoc()], aggregatingResults: 'true', id: 'java', name: 'Java'
//    recordIssues tool: errorProne()
//    junit testResults: '**/target/*-reports/TEST-*.xml'
//    recordIssues tools: [mavenConsole(),
//    checkStyle(pattern: '**/target/checkstyle-result.xml'),
//    spotBugs(pattern: '**/target/spotbugsXml.xml'),
//    pmdParser(pattern: '**/target/pmd.xml'),
//    cpd(pattern: '**/target/cpd.xml'),
//    taskScanner(highTags:'FIXME', normalTags:'TODO', includePattern: '**/*.java', excludePattern: 'target/**/*,**/TaskScannerTest.java')]

    /**
     * When switching details-tab and the page is being reloaded, the previously selected tab should be memorized and
     * still be active.
     */
    @Test
    public void shouldDoSomething() {
        FreeStyleJob job = createFreeStyleJob("cpd1Warning.xml");
        job.addPublisher(IssuesRecorder.class, recorder -> recorder.setToolWithPattern("CPD", "**/*.xml"));
        job.save();

        Build build = job.startBuild().waitUntilFinished();
        build.open();

        AnalysisSummary cpd = new AnalysisSummary(build, "cpd");

        AnalysisResult cpdDetails = cpd.openOverallResult();

        IssuesDetailsTable issuesDetailsTable = cpdDetails.openIssuesTable();

        DryIssuesTableRow firstRow = issuesDetailsTable.getRowAs(0, DryIssuesTableRow.class);
        assertThat(firstRow.getSeverity()).isEqualTo("Normal");
        assertThat(firstRow.getAge()).isEqualTo(1);
    }

    @Test
    public void shouldTest() {
        FreeStyleJob job = createFreeStyleJob("findbugs-severities.xml");
        job.addPublisher(IssuesRecorder.class, recorder -> recorder.setToolWithPattern("FindBugs", "**/*.xml"));
        job.save();

        Build build = job.startBuild().waitUntilFinished();
        build.open();

        AnalysisSummary findbugs = new AnalysisSummary(build, "findbugs");
        Assertions.assertThat(findbugs).isDisplayed();
        AnalysisResult findBugsDetails = findbugs.openOverallResult();

        Collection<Tab> tabs = findBugsDetails.getAvailableTabs();
        assertThat(tabs).contains(Tab.PACKAGES, Tab.FILES, Tab.ISSUES, Tab.TYPES, Tab.CATEGORIES);

        PackagesDetailsTable packagesDetailsTable = findBugsDetails.openPackagesTable();
        assertThat(packagesDetailsTable.getTotal()).isEqualTo(6);

        List<GenericTableRow> packageRows =  packagesDetailsTable.getTableRows();
        List<String> headers =  packagesDetailsTable.getHeaders();

        FilesDetailsTable filesDetailsTable = findBugsDetails.openFilesTable();
        assertThat(filesDetailsTable.getTotal()).isEqualTo(9);

        CategoriesDetailsTable categoriesDetailsTable = findBugsDetails.openCategoriesTable();
        assertThat(categoriesDetailsTable.getTotal()).isEqualTo(3);

        TypesDetailsTable typesDetailsTable = findBugsDetails.openTypesTable();
        assertThat(typesDetailsTable.getTotal()).isEqualTo(6);

        IssuesDetailsTable issuesDetailsTable = findBugsDetails.openIssuesTable();
        assertThat(issuesDetailsTable.getTotal()).isEqualTo(12);
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

        CategoriesDetailsTable categoriesDetailsTable = resultPage.openCategoriesTable();
        assertThat(categoriesDetailsTable.getHeaders()).containsExactlyInAnyOrder("Category", "Total", "Distribution");
        assertThat(categoriesDetailsTable.getSize()).isEqualTo(5);
        assertThat(categoriesDetailsTable.getTotal()).isEqualTo(5);

        TypesDetailsTable typesDetailsTable = resultPage.openTypesTable();
        assertThat(typesDetailsTable.getHeaders()).containsExactlyInAnyOrder("Type", "Total", "Distribution");
        assertThat(typesDetailsTable.getSize()).isEqualTo(7);
        assertThat(typesDetailsTable.getTotal()).isEqualTo(7);

        IssuesDetailsTable issuesDetailsTable = resultPage.openIssuesTable();
        assertThat(issuesDetailsTable.getHeaders()).containsExactlyInAnyOrder("Details", "File", "Category", "Type",
                "Severity", "Age");
        assertThat(issuesDetailsTable.getSize()).isEqualTo(10);
        assertThat(issuesDetailsTable.getTotal()).isEqualTo(11);

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

    private FreeStyleJob createFreeStyleJob(final String... resourcesToCopy) {
        FreeStyleJob job = jenkins.getJobs().create(FreeStyleJob.class);
        ScrollerUtil.hideScrollerTabBar(driver);
        for (String resource : resourcesToCopy) {
            job.copyResource(WARNINGS_PLUGIN_PREFIX + resource);
        }
        return job;
    }
}
