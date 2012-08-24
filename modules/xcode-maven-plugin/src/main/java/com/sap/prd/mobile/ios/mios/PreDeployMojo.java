package com.sap.prd.mobile.ios.mios;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.commons.io.IOUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.transfer.TransferCancelledException;
import org.sonatype.aether.transfer.TransferEvent;
import org.sonatype.aether.transfer.TransferListener;

/**
 * Registers a TransferListener on Aether that reacts on successful deployments. For each ipa file
 * deployed successfully a pointer file is written. This pointer file redirects to XXX
 * 
 * 
 * @goal pre-deploy
 * 
 */
public class PreDeployMojo extends AbstractXCodeMojo
{

  /**
   * The folder used for hudson archiving
   * 
   * @parameter expression="${archive.dir}" default-value="${project.build.directory}"
   * @readonly
   */
  private File archiveFolder;

  /**
   * The current repository/network configuration of Maven.
   * 
   * @parameter default-value="${repositorySystemSession}"
   * @readonly
   */
  protected RepositorySystemSession repoSession;

  private final static String HTML_TEMPLATE = "<html><head><meta http-equiv=\"refresh\" content=\"0; URL=$LOCATION\">" +
        "<body>You will be redirected within the next few seconds.<br />" +
        "In case this does not work click <a href=\"$LOCATION\">here</a></body></html>";

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException
  {
    try {
      archiveFolder = archiveFolder.getCanonicalFile();
    }
    catch (IOException ex) {
      throw new MojoExecutionException(ex.getMessage(), ex);
    }

    createArchiveFolder(archiveFolder);

    getLog().info("Using archive directory: '" + archiveFolder + "'.");

    try {

      //
      // [Q] Why do we use reflection here? What about a simple downcast to DefaultRepositorySystemSession?
      // [A] We cannot downcast since the downcast fails. The DefaultRepositorySystemSession that is available
      //     here is loaded by another class loader than the DefaultRepositorySystemSession used by the actual class.
      //     Hence we are in different runtime packages and the downcast is not possible.

      final TransferListener transferListener = (TransferListener) this.repoSession.getClass()
        .getMethod("getTransferListener", new Class[0]).invoke(this.repoSession, new Object[0]);

      TransferListener prepareIpaPointerFileTransferListener = new PrepareIpaPointerFileTransferListener(
            transferListener);

      this.repoSession.getClass().getMethod("setTransferListener", new Class[] { TransferListener.class })
        .invoke(this.repoSession, prepareIpaPointerFileTransferListener);

      getLog().info(
            "TransferListener '" + prepareIpaPointerFileTransferListener.getClass().getName() + "' has been set.");
    }
    catch (RuntimeException e) {
      throw e;
    }
    catch (Exception e) {
      throw new MojoExecutionException(
            "Cannot set transfer listener for creating artifact redirect HTML files: " + e.getMessage(), e);
    }
  }

  private static void createArchiveFolder(File archiveFolder) throws MojoExecutionException
  {
    if (archiveFolder.isFile())
      throw new MojoExecutionException("Archive folder '" + archiveFolder + "' is a file rather than a directory.");

    if (!archiveFolder.exists() && !archiveFolder.mkdirs())
      throw new MojoExecutionException("Cannot create archive folder '" + archiveFolder + "'.");
  }

  class PrepareIpaPointerFileTransferListener implements TransferListener
  {
    private TransferListener forward;

    PrepareIpaPointerFileTransferListener(TransferListener forward)
    {
      this.forward = forward;
    }

    @Override
    public void transferSucceeded(TransferEvent event)
    {
      if (forward != null)
        forward.transferSucceeded(event);

      
      if(event.getResource().getResourceName().endsWith("maven-metadata.xml")) {
        
        getLog().debug("No redirect html file will be created for '" + event.getResource().getResourceName() + "'.");
        return;
      }
      
      try {

        final String url = event.getResource().getRepositoryUrl() + event.getResource().getResourceName();
        final String html = HTML_TEMPLATE.replaceAll("\\$LOCATION", url);

        final File redirect = new File(new File(new File(new File(archiveFolder, "artifacts"), project.getGroupId()),
              project.getArtifactId()), getArtifactRedirectHtmlFileName(url));

        writeArtifactRedirectHtmlFile(redirect, url, html);
        
        getLog().info("Redirect file '" + redirect + "' written for '" + url + "'.");

      }
      catch (RuntimeException ex) {

        getLog()
          .error("Could not create artifact redirect HTML file for '" + event.getResource().getResourceName(), ex);
      }
    }

    @Override
    public void transferStarted(TransferEvent event) throws TransferCancelledException
    {
      if (forward != null)
        forward.transferStarted(event);
    }

    @Override
    public void transferProgressed(TransferEvent event) throws TransferCancelledException
    {
      if (forward != null)
        forward.transferProgressed(event);
    }

    @Override
    public void transferInitiated(TransferEvent event) throws TransferCancelledException
    {
      if (forward != null)
        forward.transferInitiated(event);
    }

    @Override
    public void transferFailed(TransferEvent event)
    {
      if (forward != null)
        forward.transferFailed(event);
    }

    @Override
    public void transferCorrupted(TransferEvent event) throws TransferCancelledException
    {
      if (forward != null)
        forward.transferCorrupted(event);
    }

    private void writeArtifactRedirectHtmlFile(final File redirect, final String url, final String html)
    {
      FileOutputStream fos = null;

      try {

        if (!redirect.getParentFile().exists() && !redirect.getParentFile().mkdirs())
          throw new IOException("Cannot create folder '" + redirect.getParentFile() + "'.");

        fos = new FileOutputStream(redirect);

        IOUtils.write(html, fos, "UTF-8");
      }
      catch (IOException ex) {
        throw new RuntimeException(ex);
      }
      finally {
        IOUtils.closeQuietly(fos);
      }
    }

    private String getArtifactRedirectHtmlFileName(final String url)
    {
      //E.g. MyApp-1.0.0-20120821.132955-1-Release-iphoneos-ota.htm 
      String artifactFileName = url.substring(url.lastIndexOf("/") + 1);
      return getRedirectHtmlFilename(artifactFileName, project.getArtifactId());
    }

  }

  static String getRedirectHtmlFilename(String artifactFileName, String artifactId)
  {
    String search = "-";
    int nthElement = 0;
    if (artifactFileName.endsWith(XCodeOtaHtmlGeneratorMojo.OTA_CLASSIFIER_APPENDIX + "."
          + XCodeOtaHtmlGeneratorMojo.OTA_HTML_FILE_APPENDIX)) {
      nthElement = 3;
    }
    else if (artifactFileName.endsWith("-AppStoreMetaData.zip")) {
      nthElement = 1;
    }
    else if (artifactFileName.endsWith("-app.dSYM.zip")) {
      nthElement = 3;
    }
    else if (artifactFileName.endsWith("-app.zip")) {
      nthElement = 3;
    }
    else if (artifactFileName.endsWith(".ipa")) {
      nthElement = 2;
    }
    else if (artifactFileName.endsWith("versions.xml")) {
      nthElement = 1;
    }
    else if (artifactFileName.endsWith(".pom")) {
      nthElement = 1;
      search = ".";
    }

    int idx = getNthIndexFromBack(artifactFileName, search, nthElement);
    if (idx >= 0) {
      String name = artifactId + artifactFileName.substring(idx);
      if (!name.endsWith(".htm")) {
        name = name + ".htm";
      }
      return name;
    }
    return artifactId + "-" + artifactFileName + ".htm";
  }

  static int getNthIndexFromBack(String string, String searchString, int countFromBack)
  {
    if (countFromBack <= 0) return -1;
    int idx = string.length();
    for (int i = 0; i < countFromBack; i++) {
      idx = string.substring(0, idx).lastIndexOf(searchString);
      if (idx <= 0) return idx;
    }
    return idx;
  }

}
