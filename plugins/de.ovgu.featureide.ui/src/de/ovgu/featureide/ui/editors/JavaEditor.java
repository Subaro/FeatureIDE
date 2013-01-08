/* FeatureIDE - An IDE to support feature-oriented software development
* Copyright (C) 2005-2012 FeatureIDE team, University of Magdeburg
*
* This program is free software; you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation; either version 2 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program. If not, see http://www.gnu.org/licenses/.
*
* See http://www.fosd.de/featureide/ for further information.
*/
package de.ovgu.featureide.ui.editors;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitEditor;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.PartInitException;
import de.ovgu.featureide.core.CorePlugin;
import de.ovgu.featureide.core.IFeatureProject;
import de.ovgu.featureide.core.builder.IComposerExtension;
import de.ovgu.featureide.ui.UIPlugin;
/**
* This Editor extends the standard Java editor {@link CompilationUnitEditor}
* It only sets the part name to differ a source from a composed file.
*
* @author Jens Meinicke
*
*/
/*
* TODO maybe the BasicJavaEditorActionContributor should used at plugin.xml
* TODO the images for composed and source files should differ(?)
*/
@SuppressWarnings("restriction")
public class JavaEditor extends CompilationUnitEditor {
public static final String ID = UIPlugin.PLUGIN_ID + ".editors.JavaEditor";
private static final Image TITLE_IMAGE = UIPlugin.getImage("JakFileIcon.png");
private IComposerExtension composer;
@Override
public void init(IEditorSite site, IEditorInput input)
throws PartInitException {
super.init(site, input);
if (input instanceof IFileEditorInput) {
IFile file = ((IFileEditorInput) input).getFile();
IFeatureProject featureProject = CorePlugin.getFeatureProject(file);
// check that the project is a FeatureIDE project and registered
if (featureProject == null)
return;
composer = featureProject.getComposer();
if (composer.hasFeatureFolder()) {
String feature = featureProject.getFeatureName(file);
if (feature != null) {
// case: a source file
if (composer.hasFeatureFolders()){
setPartName(file.getName() + "[" + feature + "]");
}
} else {
if (isComposedFile(file.getParent(), featureProject.getBuildFolder())) {
// case: a composed file
IFile configuration = featureProject.getCurrentConfiguration();
if (configuration != null) {
String config = configuration.getName().split("[.]")[0];
if (config != null) {
setPartName(file.getName() + "<" + config + ">");
}
}
} else {
String configuration = getConfiguration(file.getParent());
if (configuration != null) {
// case: a generated products file
setPartName(file.getName() + "<" + configuration + ">");
}
}
}
}
setTitleImage(TITLE_IMAGE);
}
}
/**
* Looks for the corresponding configuration file<br>
* Necessary for generated products
* @param parent
* @return The name of the configuration or <code>null</code> if there is none
*/
private String getConfiguration(IContainer parent) {
try {
for (IResource res : parent.members()) {
if (res instanceof IFile) {
if (composer.getConfigurationExtension().equals(res.getFileExtension())) {
return res.getName().split("[.]")[0];
}
}
}
} catch (CoreException e) {
UIPlugin.getDefault().logError(e);
}
IContainer p = parent.getParent();
if (p != null) {
return getConfiguration(p);
}
return null;
}
/**
* @param parent
* @param buildFolder
* @return <code>true</code> if the build folder is a parent of the given file
*/
private boolean isComposedFile(IContainer parent, IFolder buildFolder) {
if (parent != null) {
if (parent.equals(buildFolder)) {
return true;
} else {
return isComposedFile(parent.getParent(), buildFolder);
}
}
return false;
}
} 