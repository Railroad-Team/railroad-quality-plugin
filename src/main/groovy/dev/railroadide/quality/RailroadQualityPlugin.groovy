package dev.railroadide.quality

import com.diffplug.gradle.spotless.SpotlessExtension
import dev.railroadide.railroad.formatter.RailroadJavaStyle
import dev.railroadide.railroad.formatter.ClosingBraceSpacing
import dev.railroadide.railroad.formatter.SingleLineJavadocStyle
import dev.railroadide.railroad.docs.JavadocCoverage
import freemarker.template.Configuration
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.GradleException
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService

class RailroadQualityPlugin implements Plugin<Project> {
    void apply(Project project) {
        project.pluginManager.apply('java')
        project.pluginManager.apply('com.diffplug.spotless')
        def extension = project.extensions.create('railroadQuality', RailroadQualityExtension)
        def java = project.extensions.getByType(JavaPluginExtension)
        def sourceSets = project.extensions.getByType(SourceSetContainer)
        extension.toolJavaVersion.convention(25)
        extension.languageVersion.convention(java.toolchain.languageVersion.map {
            it.asInt()
        }
        .orElse(project.provider {
            Integer.parseInt(java.sourceCompatibility.majorVersion)
        }))
        extension.formatAll.convention(project.providers.gradleProperty('formatAll').map {
            true
        }.orElse(true))
        extension.ratchetFrom.convention(project.providers.gradleProperty('formatRatchetFrom').orElse('HEAD'))
        extension.checkJavadoc.convention(false)
        extension.javaSources.from(project.provider {
            sourceSets.collect {
                it.allJava
            }
        })
        extension.coverageSources.from(project.provider {
            sourceSets.main.allJava
        })
        extension.reportDirectory.convention(project.layout.buildDirectory.dir('reports/javadoc-coverage'))

        def configDirectory = project.layout.buildDirectory.dir('railroad-quality/config')
        def configContents = [
            'railroad-eclipse-formatter.xml',
            'railroad-groovy.properties'
        ].collectEntries { name ->
            RailroadQualityPlugin.getResourceAsStream(name).withCloseable { input ->
                [(name): new String(input.readAllBytes(), 'UTF-8')]
            }
        }
        def extract = project.tasks.register('railroadQualityConfig') { task ->
            task.inputs.properties(configContents)
            task.outputs.dir(configDirectory)
            task.doLast {
                def directory = configDirectory.get().asFile
                directory.mkdirs()
                configContents.each { name, contents ->
                    new File(directory, name).setText(contents, 'UTF-8')
                }
            }
        }
        def spotless = project.extensions.getByType(SpotlessExtension)
        // Defer until consumers have configured their source sets and extension.
        project.afterEvaluate {
            if (!extension.formatAll.get() && !project.providers.gradleProperty('formatAll').isPresent()) {
                spotless.ratchetFrom(extension.ratchetFrom.get())
            }
            spotless.java {
                target(extension.javaSources)
                custom('multilineJavadoc') { source ->
                    SingleLineJavadocStyle.format(source)
                }
                eclipse().configFile(configDirectory.get().file('railroad-eclipse-formatter.xml').asFile)
                custom('closingBraceSpacing') { source ->
                    ClosingBraceSpacing.format(source)
                }
                trimTrailingWhitespace()
                endWithNewline()
            }
            spotless.format('css') {
                target('src/**/*.css')
                lineEndings('UNIX')
                prettier('3.6.2').config(['parser': 'css', 'tabWidth': 4, 'printWidth': 1000])
                trimTrailingWhitespace()
                endWithNewline()
            }
            spotless.groovy {
                target('src/**/*.groovy')
                greclipse().configFile(configDirectory.get().file('railroad-groovy.properties').asFile)
                trimTrailingWhitespace()
                endWithNewline()
            }
            spotless.groovyGradle {
                target('*.gradle', 'gradle/**/*.gradle', 'src/**/*.gradle')
                greclipse().configFile(configDirectory.get().file('railroad-groovy.properties').asFile)
                trimTrailingWhitespace()
                endWithNewline()
            }
        }
        project.tasks.matching {
            it.name.startsWith('spotless') && it.name != 'spotlessDiagnose'
        }
        .configureEach { dependsOn(extract) }

        // Run tools in a forked JDK; keep compiler APIs out of the Gradle daemon.
        def toolClasspath = project.files(
                RailroadJavaStyle.class.protectionDomain.codeSource.location,
                Configuration.class.protectionDomain.codeSource.location)
        def template = JavadocCoverage.class.getResource('report.ftlh')
        if (template.protocol == 'file') {
            def resources = new File(template.toURI()).parentFile
            4.times { resources = resources.parentFile }
            toolClasspath.from(resources)
        }
        def launcher = project.extensions.getByType(JavaToolchainService).launcherFor {
            languageVersion.set(extension.toolJavaVersion.map {
                JavaLanguageVersion.of(it)
            })
        }
        ['semanticFormat': '--apply', 'semanticFormatCheck': '--check'].each { name, mode ->
            project.tasks.register(name, JavaExec) { task ->
                task.group = mode == '--apply' ? 'formatting' : 'verification'
                task.description = 'Runs syntax-aware Railroad Java style rules.'
                task.classpath = toolClasspath
                task.mainClass.set('dev.railroadide.railroad.formatter.RailroadJavaStyle')
                task.javaLauncher.set(launcher)
                task.doFirst {
                    task.systemProperty('railroad.quality.release', extension.languageVersion.get())
                    def roots = project.files(sourceSets.collect {
                        it.allJava.srcDirs
                    })
                    def compilerClasspath = new File(task.temporaryDir, 'compiler-classpath.txt')
                    compilerClasspath.setText(project.files(sourceSets.collect {
                        it.compileClasspath
                    }).asPath, 'UTF-8')
                    task.environment('RAILROAD_FORMAT_CLASSPATH_FILE', compilerClasspath.absolutePath)
                    def arguments = [mode]
                    if (extension.formatAll.get() || project.providers.gradleProperty('formatAll').isPresent()) {
                        arguments.add('--all')
                    } else {
                        arguments.addAll([
                            '--ratchet-from',
                            extension.ratchetFrom.get(),
                            project.projectDir.absolutePath
                        ])
                    }
                    arguments.addAll(['--source-path', roots.asPath])
                    // A manifest avoids Windows command-line limits on large projects.
                    def manifest = new File(task.temporaryDir, 'sources.txt')
                    manifest.setText(extension.javaSources.files*.absolutePath.sort().join('\n'), 'UTF-8')
                    arguments.addAll([
                        '--files-from',
                        manifest.absolutePath
                    ])
                    task.setArgs(arguments)
                }
            }
        }
        project.tasks.named('spotlessApply') { mustRunAfter('semanticFormat') }
        project.tasks.named('spotlessCheck') {
            mustRunAfter('semanticFormatCheck')
        }
        project.tasks.register('format') {
            group = 'formatting'
            description = 'Applies Java, CSS, Groovy, and Gradle formatting.'
            dependsOn('semanticFormat', 'spotlessApply')
        }
        project.tasks.register('formatCheck') {
            group = 'verification'
            description = 'Checks Java, CSS, Groovy, and Gradle formatting.'
            dependsOn('semanticFormatCheck', 'spotlessCheck')
        }
        project.tasks.register('javadocCoverage', JavaExec) { task ->
            task.group = 'documentation'
            task.description = 'Generates searchable HTML coverage for public API Javadocs.'
            task.classpath = toolClasspath
            task.mainClass.set('dev.railroadide.railroad.docs.JavadocCoverage')
            task.javaLauncher.set(launcher)
            task.inputs.files(extension.coverageSources).withPathSensitivity(PathSensitivity.RELATIVE)
            task.inputs.property('languageVersion', extension.languageVersion)
            task.outputs.dir(extension.reportDirectory)
            task.doFirst {
                task.systemProperty('railroad.quality.release', extension.languageVersion.get())
                def manifest = new File(task.temporaryDir, 'sources.txt')
                manifest.setText(extension.coverageSources.files*.absolutePath.sort().join('\n'), 'UTF-8')
                task.setArgs([
                    project.projectDir.absolutePath,
                    manifest.absolutePath,
                    extension.reportDirectory.get().asFile.absolutePath
                ])
            }
        }
        project.tasks.register('javadocCoverageCheck') {
            group = 'verification'
            description = 'Fails if public API Javadocs are incomplete.'
            dependsOn('javadocCoverage')
            doLast {
                def report = extension.reportDirectory.get().asFile
                def violations = new File(report, 'violations.txt').getText('UTF-8').trim().toLong()
                if (violations> 0) {
                    throw new GradleException("${violations} public API declarations have incomplete Javadocs. Report: ${new File(report, 'index.html').toURI()}")
                }
            }
        }
        project.afterEvaluate {
            if (extension.checkJavadoc.get()) {
                project.tasks.named('check') {
                    dependsOn('javadocCoverageCheck')
                }
            }
        }
    }
}
