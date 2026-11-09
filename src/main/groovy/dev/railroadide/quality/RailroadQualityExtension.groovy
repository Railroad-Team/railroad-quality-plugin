package dev.railroadide.quality

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property

abstract class RailroadQualityExtension {
    abstract Property<Integer> getToolJavaVersion()
    abstract Property<Integer> getLanguageVersion()
    abstract Property<Boolean> getFormatAll()
    abstract Property<String> getRatchetFrom()
    abstract Property<Boolean> getCheckJavadoc()
    abstract ConfigurableFileCollection getJavaSources()
    abstract ConfigurableFileCollection getCoverageSources()
    abstract DirectoryProperty getReportDirectory()
}
