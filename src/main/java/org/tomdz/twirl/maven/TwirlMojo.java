package org.tomdz.twirl.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

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
     * @parameter default-value="${basedir}/src/main/twirl"
     */
    private File sourceDirectory;

    /**
     * Specifies the destination directory where compiled templates should be put.
     * 
     * @parameter default-value="${project.build.directory}/generated-sources/twirl"
     */
    private File outputDirectory;

     /**
      * The charset to use when reading twirl sources and writing template .scala files.
      *
      * @parameter default-value="UTF-8"
      */
     private String sourceCharset;

     /**
     * Additional imports available to the twirl templates.
     *
     * @parameter
     */
    private List<String> additionalImports = new ArrayList<String>();

    public void execute() throws MojoExecutionException
    {
        getLog().info("Compiling all twirl templates in " + sourceDirectory.getAbsolutePath());
        TemplateCompiler.compile(sourceDirectory,
                                 outputDirectory,
                                 Charset.forName(sourceCharset),
                                 additionalImports,
                                 getLog());
        if (project != null) {
            project.addCompileSourceRoot(outputDirectory.getAbsolutePath());
        }
    }
}