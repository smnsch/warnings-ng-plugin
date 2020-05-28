package io.jenkins.plugins.analysis.warnings;

import org.junit.Test;
import org.jenkinsci.test.acceptance.junit.AbstractJUnitTest;
import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.jenkinsci.test.acceptance.po.FreeStyleJob;
import io.jenkins.plugins.analysis.warnings.IssuesRecorder.TrendChartType;
import static io.jenkins.plugins.analysis.warnings.Assertions.*;

/**
 * Acceptance tests for the Freestyle configuration of the Warnings Next Generation Plugin.

 */
@WithPlugins("warnings-ng")
@SuppressWarnings({"checkstyle:ClassFanOutComplexity", "PMD.SystemPrintln", "PMD.ExcessiveImports"})
public class FreeStyleConfigurationUITest extends AbstractJUnitTest {

    private static final String PATTERN = "**/*.txt";
    private static final String ENCODING = "UTF-8";
    private static final String REFERENCE = "reference";
    private static final String SOURCE_DIRECTORY = "relative";
    private static final String SEVERITY = "NORMAL";

    /**
     * Verifies that job configuration screen correctly modifies the properties of an {@link IssuesRecorder} instance.
     */
    @Test
    public void shouldSetPropertiesInJobConfiguration() {
        FreeStyleJob job = jenkins.getJobs().create(FreeStyleJob.class);


        IssuesRecorder issuesRecorder = job.addPublisher(IssuesRecorder.class, recorder -> {
            recorder.setTool("Eclipse ECJ");
        });

        issuesRecorder.setSourceCodeEncoding(ENCODING);
        issuesRecorder.setSourceDirectory(SOURCE_DIRECTORY);
        issuesRecorder.setAggregatingResults(true);
        issuesRecorder.setTrendChartType(TrendChartType.TOOLS_ONLY);
        issuesRecorder.setBlameDisabled(true);
        issuesRecorder.setForensicsDisabled(true);
        issuesRecorder.setEnabledForFailure(true);
        issuesRecorder.setIgnoreQualityGate(true);
        issuesRecorder.setIgnoreFailedBuilds(true);
        issuesRecorder.setFailOnError(true);
        issuesRecorder.setReferenceJobField(REFERENCE);
        issuesRecorder.setHealthReport(1, 9, SEVERITY);
        issuesRecorder.setReportFilePattern(PATTERN);


        job.save();
        job.configure();
        issuesRecorder.openAdvancedOptions();
        assertThat(issuesRecorder).hasSourceCodeEncoding(ENCODING);
        assertThat(issuesRecorder).hasSourceDirectory(SOURCE_DIRECTORY);
        assertThat(issuesRecorder).hasAggregatingResults(true);
        assertThat(issuesRecorder).hasTrendChartType(TrendChartType.TOOLS_ONLY.toString());
        assertThat(issuesRecorder).hasBlameDisabled(true);
        assertThat(issuesRecorder).hasForensicsDisabled(true);
        assertThat(issuesRecorder).hasEnabledForFailure(true);
        assertThat(issuesRecorder).hasIgnoreQualityGate(true);
        assertThat(issuesRecorder).hasIgnoreFailedBuilds(true);
        assertThat(issuesRecorder).hasFailOnError(true);
        assertThat(issuesRecorder).hasReferenceJobField(REFERENCE);
        assertThat(issuesRecorder).hasHealthThreshold("1");
        assertThat(issuesRecorder).hasUnhealthyThreshold("9");
        assertThat(issuesRecorder).hasHealthSeverity(SEVERITY);
        assertThat(issuesRecorder).hasReportFilePattern(PATTERN);

        // Now invert all booleans:
        issuesRecorder.setAggregatingResults(false);
        issuesRecorder.setBlameDisabled(false);
        issuesRecorder.setForensicsDisabled(false);
        issuesRecorder.setEnabledForFailure(false);
        issuesRecorder.setIgnoreQualityGate(false);
        issuesRecorder.setIgnoreFailedBuilds(false);
        issuesRecorder.setFailOnError(false);
        job.save();
        job.configure();
        issuesRecorder.openAdvancedOptions();

        assertThat(issuesRecorder).hasAggregatingResults(false);
        assertThat(issuesRecorder).hasBlameDisabled(false);
        assertThat(issuesRecorder).hasForensicsDisabled(false);
        assertThat(issuesRecorder).hasEnabledForFailure(false);
        assertThat(issuesRecorder).hasIgnoreQualityGate(false);
        assertThat(issuesRecorder).hasIgnoreFailedBuilds(false);
        assertThat(issuesRecorder).hasFailOnError(false);
    }
}