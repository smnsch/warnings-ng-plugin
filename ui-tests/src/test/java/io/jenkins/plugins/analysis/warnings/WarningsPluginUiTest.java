package io.jenkins.plugins.analysis.warnings;

import org.junit.Test;

import com.google.inject.Inject;

import org.jenkinsci.test.acceptance.docker.DockerContainer;
import org.jenkinsci.test.acceptance.docker.DockerContainerHolder;
import org.jenkinsci.test.acceptance.docker.fixtures.JavaGitContainer;
import org.jenkinsci.test.acceptance.junit.WithCredentials;
import org.jenkinsci.test.acceptance.junit.WithDocker;
import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.jenkinsci.test.acceptance.plugins.maven.MavenModuleSet;
import org.jenkinsci.test.acceptance.plugins.ssh_slaves.SshSlaveLauncher;
import org.jenkinsci.test.acceptance.po.Build;
import org.jenkinsci.test.acceptance.po.DumbSlave;
import org.jenkinsci.test.acceptance.po.FreeStyleJob;
import org.jenkinsci.test.acceptance.po.Slave;

import io.jenkins.plugins.analysis.warnings.AnalysisResult.Tab;
import io.jenkins.plugins.analysis.warnings.IssuesRecorder.QualityGateBuildResult;
import io.jenkins.plugins.analysis.warnings.IssuesRecorder.QualityGateType;

import static io.jenkins.plugins.analysis.warnings.Assertions.*;

/**
 * Acceptance tests for the Warnings Next Generation Plugin.
 *
 * @author Frank Christian Geyer
 * @author Ullrich Hafner
 * @author Manuel Hampp
 * @author Anna-Maria Hardi
 * @author Elvira Hauer
 * @author Deniz Mardin
 * @author Stephan Plöderl
 * @author Alexander Praegla
 * @author Michaela Reitschuster
 * @author Arne Schöntag
 * @author Alexandra Wenzel
 * @author Nikolai Wohlgemuth
 * @author Florian Hageneder
 * @author Veronika Zwickenpflug
 */
@WithPlugins("warnings-ng")
@SuppressWarnings({"checkstyle:ClassFanOutComplexity", "PMD.SystemPrintln", "PMD.ExcessiveImports"})
public class WarningsPluginUiTest extends UiTest {
    private static final String SOURCE_VIEW_FOLDER = "/source-view/";

    /**
     * Credentials to access the docker container. The credentials are stored with the specified ID and use the provided
     * SSH key. Use the following annotation on your test case to use the specified docker container as git server or
     * build agent:
     * <blockquote>
     * <pre>@Test @WithDocker @WithCredentials(credentialType = WithCredentials.SSH_USERNAME_PRIVATE_KEY,
     *                                    values = {CREDENTIALS_ID, CREDENTIALS_KEY})}
     * public void shouldTestWithDocker() {
     * }
     * </pre></blockquote>
     */
    private static final String CREDENTIALS_ID = "git";
    private static final String CREDENTIALS_KEY = "/org/jenkinsci/test/acceptance/docker/fixtures/GitContainer/unsafe";

    @Inject
    private DockerContainerHolder<JavaGitContainer> dockerContainer;

    /**
     * Runs a pipeline with all tools two times. Verifies the analysis results in several views. Additionally, verifies
     * the expansion of tokens with the token-macro plugin.
     */
    @Test
    @WithPlugins({"token-macro", "pipeline-stage-step", "workflow-durable-task-step", "workflow-basic-steps"})
    public void shouldRecordIssuesInPipelineAndExpandTokens() {
        WorkflowJob job = jenkins.jobs.create(WorkflowJob.class);
        job.sandbox.check();

        createRecordIssuesStep(job, 1);

        job.save();

        Build referenceBuild = buildJob(job);

        assertThat(referenceBuild.getConsole())
                .contains("[total=4]")
                .contains("[new=0]")
                .contains("[fixed=0]")
                .contains("[checkstyle=1]")
                .contains("[pmd=3]");

        job.configure(() -> createRecordIssuesStep(job, 2));

        Build build = buildJob(job);

        assertThat(build.getConsole())
                .contains("[total=25]")
                .contains("[new=23]")
                .contains("[fixed=2]")
                .contains("[checkstyle=3]")
                .contains("[pmd=2]");

        verifyPmd(build);
        verifyFindBugs(build);
        verifyCheckStyle(build);
        verifyCpd(build);
    }

    private void createRecordIssuesStep(final WorkflowJob job, final int build) {
        job.script.set("node {\n"
                + createReportFilesStep(job, build)
                + "recordIssues tool: checkStyle(pattern: '**/checkstyle*')\n"
                + "recordIssues tool: pmdParser(pattern: '**/pmd*')\n"
                + "recordIssues tools: [cpd(pattern: '**/cpd*', highThreshold:8, normalThreshold:3), findBugs()], aggregatingResults: 'false' \n"
                + "def total = tm('${ANALYSIS_ISSUES_COUNT}')\n"
                + "echo '[total=' + total + ']' \n"
                + "def checkstyle = tm('${ANALYSIS_ISSUES_COUNT, tool=\"checkstyle\"}')\n"
                + "echo '[checkstyle=' + checkstyle + ']' \n"
                + "def pmd = tm('${ANALYSIS_ISSUES_COUNT, tool=\"pmd\"}')\n"
                + "echo '[pmd=' + pmd + ']' \n"
                + "def newSize = tm('${ANALYSIS_ISSUES_COUNT, type=\"NEW\"}')\n"
                + "echo '[new=' + newSize + ']' \n"
                + "def fixedSize = tm('${ANALYSIS_ISSUES_COUNT, type=\"FIXED\"}')\n"
                + "echo '[fixed=' + fixedSize + ']' \n"
                + "}");
    }

    private StringBuilder createReportFilesStep(final WorkflowJob job, final int build) {
        String[] fileNames = {"checkstyle-result.xml", "pmd.xml", "findbugsXml.xml", "cpd.xml", "Main.java"};
        StringBuilder resourceCopySteps = new StringBuilder();
        for (String fileName : fileNames) {
            resourceCopySteps.append(job.copyResourceStep(WARNINGS_PLUGIN_PREFIX
                    + "build_status_test/build_0" + build + "/" + fileName).replace("\\", "\\\\"));
        }
        return resourceCopySteps;
    }

    /**
     * Runs a freestyle job with all tools two times. Verifies the analysis results in several views.
     */
    @Test
    public void shouldShowBuildSummaryAndLinkToDetails() {
        FreeStyleJob job = createFreeStyleJob("build_status_test/build_01");
        addRecorder(job);
        job.save();

        buildJob(job);

        reconfigureJobWithResource(job, "build_status_test/build_02");

        Build build = buildJob(job);

        verifyPmd(build);
        verifyFindBugs(build);
        verifyCheckStyle(build);
        verifyCpd(build);
    }

    private void verifyCpd(final Build build) {
        build.open();

        AnalysisSummary cpd = new AnalysisSummary(build, CPD_ID);
        assertThat(cpd).isDisplayed()
                .hasTitleText("CPD: 20 warnings")
                .hasNewSize(20)
                .hasFixedSize(0)
                .hasReferenceBuild(1)
                .hasInfoType(InfoType.INFO);

        AnalysisResult cpdDetails = cpd.openOverallResult();
        assertThat(cpdDetails).hasActiveTab(Tab.ISSUES).hasOnlyAvailableTabs(Tab.ISSUES);

        IssuesDetailsTable issuesDetailsTable = cpdDetails.openIssuesTable();
        assertThat(issuesDetailsTable).hasSize(10).hasTotal(20);

        DryIssuesTableRow firstRow = issuesDetailsTable.getRowAs(0, DryIssuesTableRow.class);
        DryIssuesTableRow secondRow = issuesDetailsTable.getRowAs(1, DryIssuesTableRow.class);

        firstRow.toggleDetailsRow();
        assertThat(issuesDetailsTable).hasSize(11);

        DetailsTableRow detailsRow = issuesDetailsTable.getRowAs(1, DetailsTableRow.class);
        assertThat(detailsRow).hasDetails("Found duplicated code.\nfunctionOne();");

        assertThat(issuesDetailsTable.getRowAs(2, DryIssuesTableRow.class)).isEqualTo(secondRow);

        firstRow.toggleDetailsRow();
        assertThat(issuesDetailsTable).hasSize(10);
        assertThat(issuesDetailsTable.getRowAs(1, DryIssuesTableRow.class)).isEqualTo(secondRow);

        SourceView sourceView = firstRow.openSourceCode();
        assertThat(sourceView).hasFileName(CPD_SOURCE_NAME);

        String expectedSourceCode = readFileToString(WARNINGS_PLUGIN_PREFIX + CPD_SOURCE_PATH);
        assertThat(sourceView.getSourceCode()).isEqualToIgnoringWhitespace(expectedSourceCode);

        cpdDetails.open();
        issuesDetailsTable = cpdDetails.openIssuesTable();
        firstRow = issuesDetailsTable.getRowAs(0, DryIssuesTableRow.class);

        AnalysisResult lowSeverity = firstRow.clickOnSeverityLink();
        IssuesDetailsTable lowSeverityTable = lowSeverity.openIssuesTable();
        assertThat(lowSeverityTable).hasSize(6).hasTotal(6);

        for (int i = 0; i < 6; i++) {
            DryIssuesTableRow row = lowSeverityTable.getRowAs(i, DryIssuesTableRow.class);
            assertThat(row).hasSeverity(WARNING_LOW_PRIORITY);
        }

        build.open();
        assertThat(openInfoView(build, CPD_ID))
                .hasNoErrorMessages()
                .hasInfoMessages("-> found 1 file",
                        "-> found 20 issues (skipped 0 duplicates)",
                        "-> 1 copied, 0 not in workspace, 0 not-found, 0 with I/O error",
                        "Issues delta (vs. reference build): outstanding: 0, new: 20, fixed: 0");

    }

    private void verifyFindBugs(final Build build) {
        build.open();

        AnalysisSummary findbugs = new AnalysisSummary(build, FINDBUGS_ID);
        assertThat(findbugs).isDisplayed()
                .hasTitleText("FindBugs: No warnings")
                .hasNewSize(0)
                .hasFixedSize(0)
                .hasReferenceBuild(1)
                .hasInfoType(InfoType.INFO)
                .hasDetails("No warnings for 2 builds, i.e. since build 1");

        build.open();
        assertThat(openInfoView(build, FINDBUGS_ID))
                .hasNoErrorMessages()
                .hasInfoMessages("-> found 1 file",
                        "-> found 0 issues (skipped 0 duplicates)",
                        "Issues delta (vs. reference build): outstanding: 0, new: 0, fixed: 0");
    }

    private void verifyPmd(final Build build) {
        build.open();
        AnalysisSummary pmd = new AnalysisSummary(build, PMD_ID);
        assertThat(pmd).isDisplayed()
                .hasTitleText("PMD: 2 warnings")
                .hasNewSize(0)
                .hasFixedSize(1)
                .hasReferenceBuild(1)
                .hasInfoType(InfoType.ERROR);

        AnalysisResult pmdDetails = pmd.openOverallResult();
        assertThat(pmdDetails).hasActiveTab(Tab.CATEGORIES)
                .hasTotal(2)
                .hasOnlyAvailableTabs(Tab.CATEGORIES, Tab.TYPES, Tab.ISSUES);

        build.open();
        assertThat(openInfoView(build, PMD_ID))
                .hasInfoMessages("-> found 1 file",
                        "-> found 2 issues (skipped 0 duplicates)",
                        "Issues delta (vs. reference build): outstanding: 2, new: 0, fixed: 1")
                .hasErrorMessages("Can't create fingerprints for some files:");
    }

    private void verifyCheckStyle(final Build build) {
        build.open();
        AnalysisSummary checkstyle = new AnalysisSummary(build, CHECKSTYLE_ID);
        assertThat(checkstyle).isDisplayed()
                .hasTitleText("CheckStyle: 3 warnings")
                .hasNewSize(3)
                .hasFixedSize(1)
                .hasReferenceBuild(1)
                .hasInfoType(InfoType.ERROR);

        AnalysisResult checkstyleDetails = checkstyle.openOverallResult();
        assertThat(checkstyleDetails).hasActiveTab(Tab.CATEGORIES)
                .hasTotal(3)
                .hasOnlyAvailableTabs(Tab.CATEGORIES, Tab.TYPES, Tab.ISSUES);

        IssuesDetailsTable issuesDetailsTable = checkstyleDetails.openIssuesTable();
        assertThat(issuesDetailsTable).hasSize(3).hasTotal(3);

        DefaultIssuesTableRow tableRow = issuesDetailsTable.getRowAs(0, DefaultIssuesTableRow.class);
        assertThat(tableRow).hasFileName("RemoteLauncher.java")
                .hasLineNumber(59)
                .hasCategory("Checks")
                .hasType("FinalParametersCheck")
                .hasSeverity("Error")
                .hasAge(1);

        build.open();
        assertThat(openInfoView(build, CHECKSTYLE_ID))
                .hasInfoMessages("-> found 1 file",
                        "-> found 3 issues (skipped 0 duplicates)",
                        "Issues delta (vs. reference build): outstanding: 0, new: 3, fixed: 1")
                .hasErrorMessages("Can't create fingerprints for some files:");
    }

    private InfoView openInfoView(final Build build, final String toolId) {
        return new AnalysisSummary(build, toolId).openInfoView();
    }

    /**
     * Tests the build overview page by running two builds that aggregate the three different tools into a single
     * result. Checks the contents of the result summary.
     */
    @Test
    public void shouldAggregateToolsIntoSingleResult() {
        FreeStyleJob job = createFreeStyleJob("build_status_test/build_01");
        IssuesRecorder recorder = addAllRecorders(job);
        recorder.setEnabledForAggregation(true);
        recorder.addQualityGateConfiguration(4, QualityGateType.TOTAL, QualityGateBuildResult.UNSTABLE);
        recorder.addQualityGateConfiguration(3, QualityGateType.NEW, QualityGateBuildResult.FAILED);
        recorder.setIgnoreQualityGate(true);

        job.save();

        Build referenceBuild = buildJob(job).shouldBeUnstable();
        referenceBuild.open();

        assertThat(new AnalysisSummary(referenceBuild, CHECKSTYLE_ID)).isNotDisplayed();
        assertThat(new AnalysisSummary(referenceBuild, PMD_ID)).isNotDisplayed();
        assertThat(new AnalysisSummary(referenceBuild, FINDBUGS_ID)).isNotDisplayed();

        AnalysisSummary referenceSummary = new AnalysisSummary(referenceBuild, ANALYSIS_ID);
        assertThat(referenceSummary).isDisplayed()
                .hasTitleText("Static Analysis: 4 warnings")
                .hasAggregation("FindBugs, CPD, CheckStyle, PMD")
                .hasNewSize(0)
                .hasFixedSize(0)
                .hasReferenceBuild(0);

        reconfigureJobWithResource(job, "build_status_test/build_02");

        Build build = buildJob(job);

        build.open();

        AnalysisSummary analysisSummary = new AnalysisSummary(build, ANALYSIS_ID);
        assertThat(analysisSummary).isDisplayed()
                .hasTitleText("Static Analysis: 25 warnings")
                .hasAggregation("FindBugs, CPD, CheckStyle, PMD")
                .hasNewSize(23)
                .hasFixedSize(2)
                .hasReferenceBuild(1);

        AnalysisResult result = analysisSummary.openOverallResult();
        assertThat(result).hasActiveTab(Tab.TOOLS).hasTotal(25)
                .hasOnlyAvailableTabs(Tab.TOOLS, Tab.PACKAGES, Tab.FILES, Tab.CATEGORIES, Tab.TYPES, Tab.ISSUES);
    }

    /**
     * Test to check that the issue filter can be configured and is applied.
     */
    @Test
    public void shouldFilterIssuesByIncludeAndExcludeFilters() {
        FreeStyleJob job = createFreeStyleJob("issue_filter/checkstyle-result.xml");
        job.addPublisher(IssuesRecorder.class, recorder -> {
            recorder.setTool("CheckStyle");
            recorder.setEnabledForFailure(true);
            recorder.addIssueFilter("Exclude categories", "Checks");
            recorder.addIssueFilter("Include types", "JavadocMethodCheck");
        });

        job.save();

        Build build = buildJob(job);

        assertThat(build.getConsole()).contains(
                "Applying 2 filters on the set of 4 issues (3 issues have been removed, 1 issues will be published)");

        AnalysisResult resultPage = new AnalysisResult(build, "checkstyle");
        resultPage.open();

        IssuesDetailsTable issuesDetailsTable = resultPage.openIssuesTable();
        assertThat(issuesDetailsTable).hasSize(1);
    }

    /**
     * Creates and builds a maven job and verifies that all warnings are shown in the summary and details views.
     */
    @Test
    @WithPlugins("maven-plugin")
    public void shouldShowMavenWarningsInMavenProject() {
        MavenModuleSet job = createMavenProject();
        copyResourceFilesToWorkspace(job, SOURCE_VIEW_FOLDER + "pom.xml");

        IssuesRecorder recorder = job.addPublisher(IssuesRecorder.class);
        recorder.setToolWithPattern("Maven", "");
        recorder.setEnabledForFailure(true);

        job.save();

        Build build = buildJob(job).shouldSucceed();

        System.out.println("-------------- Console Log ----------------");
        System.out.println(build.getConsole());
        System.out.println("-------------------------------------------");

        build.open();

        AnalysisSummary summary = new AnalysisSummary(build, MAVEN_ID);
        assertThat(summary).isDisplayed()
                .hasTitleText("Maven: 4 warnings")
                .hasNewSize(0)
                .hasFixedSize(0)
                .hasReferenceBuild(0);

        AnalysisResult mavenDetails = summary.openOverallResult();
        assertThat(mavenDetails).hasActiveTab(Tab.MODULES)
                .hasTotal(4)
                .hasOnlyAvailableTabs(Tab.MODULES, Tab.TYPES, Tab.ISSUES);

        IssuesDetailsTable issuesDetailsTable = mavenDetails.openIssuesTable();

        DefaultIssuesTableRow firstRow = issuesDetailsTable.getRowAs(0, DefaultIssuesTableRow.class);
        ConsoleLogView sourceView = firstRow.openConsoleLog();
        assertThat(sourceView).hasTitle("Console Details")
                .hasHighlightedText("[WARNING]\n"
                        + "[WARNING] Some problems were encountered while building the effective model for edu.hm.hafner.irrelevant.groupId:random-artifactId:jar:1.0\n"
                        + "[WARNING] 'build.plugins.plugin.version' for org.apache.maven.plugins:maven-compiler-plugin is missing. @ line 13, column 15\n"
                        + "[WARNING]\n"
                        + "[WARNING] It is highly recommended to fix these problems because they threaten the stability of your build.\n"
                        + "[WARNING]\n"
                        + "[WARNING] For this reason, future Maven versions might no longer support building such malformed projects.\n"
                        + "[WARNING]");
    }

    /**
     * Verifies that warnings can be parsed on a agent as well.
     */
    @Test
    @WithDocker
    @WithPlugins("ssh-slaves")
    @WithCredentials(credentialType = WithCredentials.SSH_USERNAME_PRIVATE_KEY, values = {CREDENTIALS_ID, CREDENTIALS_KEY})
    public void shouldParseWarningsOnAgent() {
        DumbSlave dockerAgent = createDockerAgent();
        FreeStyleJob job = createFreeStyleJobForDockerAgent(dockerAgent, "issue_filter/checkstyle-result.xml");
        job.addPublisher(IssuesRecorder.class, recorder -> recorder.setTool("CheckStyle", "**/checkstyle-result.xml"));
        job.save();

        Build build = buildJob(job);
        build.open();

        AnalysisSummary summary = new AnalysisSummary(build, "checkstyle");
        assertThat(summary).isDisplayed()
                .hasTitleText("CheckStyle: 4 warnings")
                .hasNewSize(0)
                .hasFixedSize(0)
                .hasReferenceBuild(0);
    }

    private FreeStyleJob createFreeStyleJobForDockerAgent(final Slave dockerAgent, final String... resourcesToCopy) {
        FreeStyleJob job = createFreeStyleJob(resourcesToCopy);
        job.configure();
        job.setLabelExpression(dockerAgent.getName());
        return job;
    }

    /**
     * Returns a docker container that can be used to host git repositories and which can be used as build agent. If the
     * container is used as agent and git server, then you need to use the file protocol to access the git repository
     * within Jenkins.
     *
     * @return the container
     */
    private DockerContainer getDockerContainer() {
        return dockerContainer.get();
    }

    /**
     * Creates an agent in a Docker container.
     *
     * @return the new agent ready for new builds
     */
    private DumbSlave createDockerAgent() {
        DumbSlave agent = jenkins.slaves.create(DumbSlave.class);

        agent.setExecutors(1);
        agent.remoteFS.set("/tmp/");
        SshSlaveLauncher launcher = agent.setLauncher(SshSlaveLauncher.class);

        DockerContainer container = getDockerContainer();
        launcher.host.set(container.ipBound(22));
        launcher.port(container.port(22));
        launcher.setSshHostKeyVerificationStrategy(SshSlaveLauncher.NonVerifyingKeyVerificationStrategy.class);
        launcher.selectCredentials(CREDENTIALS_ID);

        agent.save();

        agent.waitUntilOnline();

        assertThat(agent.isOnline()).isTrue();

        return agent;
    }
}

