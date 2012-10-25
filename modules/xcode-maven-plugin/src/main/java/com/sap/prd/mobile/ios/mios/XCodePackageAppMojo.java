/*
 * #%L
 * xcode-maven-plugin
 * %%
 * Copyright (C) 2012 SAP AG
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package com.sap.prd.mobile.ios.mios;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProjectHelper;

/**
 * Packages the application built by Xcode and prepares the generated artifact for deployment.
 * 
 * @goal package-application
 * 
 */
public class XCodePackageAppMojo extends AbstractXCodeMojo
{
  /**
   * @component
   */
  private MavenProjectHelper projectHelper;

  /**
   * @parameter expression="${product.name}"
   */
  private String productName;

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException
  {

    try {
      for (final String sdk : getSDKs()) {
        for (final String config : getConfigurations()) {
          PackageAppTask task = new PackageAppTask();
          task.setCompileDir(getXCodeCompileDirectory()).setLog(getLog()).setMavenProject(project)
            .setProductName(productName).setProjectHelper(projectHelper).setConfiguration(config).setSdk(sdk);
          task.execute();
        }
      }
    }
    catch (XCodeException ex) {
      throw new MojoExecutionException("Cannot package the app", ex);
    }
  }

}
