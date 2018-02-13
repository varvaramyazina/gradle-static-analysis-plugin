package com.novoda.staticanalysis.internal.lint

import com.novoda.staticanalysis.StaticAnalysisExtension
import com.novoda.staticanalysis.Violations
import com.novoda.staticanalysis.internal.Configurator
import com.novoda.staticanalysis.internal.VariantAware
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.Task

class LintConfigurator implements Configurator, VariantAware {

    private final Project project
    private final Violations violations
    private final Task evaluateViolations

    static LintConfigurator create(Project project,
                                   NamedDomainObjectContainer<Violations> violationsContainer,
                                   Task evaluateViolations) {
        Violations violations = violationsContainer.maybeCreate('Lint')
        return new LintConfigurator(project, violations, evaluateViolations)
    }

    private LintConfigurator(Project project, Violations violations, Task evaluateViolations) {
        this.project = project
        this.violations = violations
        this.evaluateViolations = evaluateViolations
    }

    @Override
    void execute() {
        project.extensions.findByType(StaticAnalysisExtension).ext.lintOptions = { Closure config ->
            project.plugins.withId('com.android.application') {
                configureLint(config)
                if (includeVariantsFilter != null) {
                    filteredApplicationVariants.all {
                        configureCollectViolationsTask(it.name, "lint-results-${it.name}")
                    }
                } else {
                    configureCollectViolationsTask('lint-results')
                }
            }
            project.plugins.withId('com.android.library') {
                configureLint(config)
                if (includeVariantsFilter != null) {
                    filteredLibraryVariants.all {
                        configureCollectViolationsTask(it.name, "lint-results-${it.name}")
                    }
                } else {
                    configureCollectViolationsTask('lint-results')
                }
            }
        }
    }


    private void configureLint(Closure config) {
        project.android.lintOptions.ext.includeVariants = { Closure<Boolean> filter ->
            includeVariantsFilter = filter
        }
        project.android.lintOptions(config)
        project.android.lintOptions {
            xmlReport = true
            htmlReport = true
            abortOnError false
        }
    }

    private void configureCollectViolationsTask(String taskSuffix = '', String reportFileName) {
        def collectViolations = createCollectViolationsTask(taskSuffix, reportFileName, violations).with {
            it.dependsOn project.tasks.findByName("lint${taskSuffix.capitalize()}")
        }
        evaluateViolations.dependsOn collectViolations
    }

    private CollectLintViolationsTask createCollectViolationsTask(String taskSuffix, String reportFileName, Violations violations) {
        project.tasks.create("collectLint${taskSuffix.capitalize()}Violations", CollectLintViolationsTask) { task ->
            task.xmlReportFile = xmlOutputFileFor(reportFileName)
            task.htmlReportFile = htmlOutputFileFor(reportFileName)
            task.violations = violations
        }
    }

    private File xmlOutputFileFor(reportFileName) {
        project.android.lintOptions.xmlOutput ?: new File(defaultOutputFolder, "${reportFileName}.xml")
    }

    private File htmlOutputFileFor(reportFileName) {
        project.android.lintOptions.htmlOutput ?: new File(defaultOutputFolder, "${reportFileName}.html")
    }

    private File getDefaultOutputFolder() {
        new File(project.buildDir, 'reports')
    }

}
