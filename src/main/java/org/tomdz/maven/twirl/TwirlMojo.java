package org.tomdz.maven.twirl;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import java.io.File;

/**
 * Adds tasks to deal with Play Framework Scala template files during the Maven build lifecycle.
 *
 * @goal generate
 * @phase generate-sources
 * @requiresDependencyResolution compile
 */
 public class TwirlMojo extends AbstractMojo
 {
    /**
     * The maven project (effective pom).
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
    */
    private MavenProject project;
    /**
     * Specifies the directory containing the template files.
     * 
     * @parameter default-value="${project.build.directory}/src/main/twirl"
     */
    private File sourceDirectory;
    /**
     * Specifies the destination directory where compiled templates should be put.
     * 
     * @parameter default-value="${project.build.directory}/generated-sources/twirl"
     */
    private File outputDirectory;

    public void execute() throws MojoExecutionException
    {
        new TwirlCompiler().compile(sourceDirectory, outputDirectory);
        if (project != null) {
            project.addCompileSourceRoot(outputDirectory.getAbsolutePath());
        }
    }
}