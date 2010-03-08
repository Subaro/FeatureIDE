/* FeatureIDE - An IDE to support feature-oriented software development
 * Copyright (C) 2005-2010  FeatureIDE Team, University of Magdeburg
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 *
 * See http://www.fosd.de/featureide/ for further information.
 */
package featureide.ui.launching;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.ui.ILaunchShortcut;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;

import featureide.core.CorePlugin;
import featureide.core.IFeatureProject;

public class LayeredApplicationShortcut implements ILaunchShortcut {

    public static final String ID_LAYERED_APPLICATION = "featureide.core.launching.layeredApplication";

	public void launch(IEditorPart editor, String mode) {
        IEditorInput input = editor.getEditorInput();
        if (input instanceof IFileEditorInput)
        	searchAndLaunch(((IFileEditorInput) input).getFile(), mode);
    }

    public void launch(ISelection selection, String mode) {
        if (selection instanceof IStructuredSelection && ((IStructuredSelection) selection).size() == 1) {
        	Object item = ((IStructuredSelection) selection).getFirstElement();
        	if (item instanceof IFile)
        		searchAndLaunch((IFile) item, mode);
        } 
    }

    protected void searchAndLaunch(IFile file, String mode) {
		IFeatureProject featureProject = CorePlugin.getProjectData(file);
		if (featureProject != null && file.getName().endsWith(".jak")) {
			String jakFileName = file.getName();
			String className = jakFileName.substring(0, jakFileName.length() - ".jak".length());
			findLaunchConfiguration(file.getProject(), featureProject.getBinPath(), className, mode);
		}
    }

	private void findLaunchConfiguration(IProject project, String binPath, String className, String mode) {
		ILaunchConfiguration config = createLaunchConfiguration(project, binPath, className);
		try {
			config.launch(mode, null);
		} catch (CoreException e) {
			e.printStackTrace();
		}
	}

	private ILaunchConfiguration createLaunchConfiguration(IProject project, String binPath, String className) {
		ILaunchConfiguration config = null;
		try {
			ILaunchConfigurationType configType = getConfigurationType();
			ILaunchConfigurationWorkingCopy wc = configType.newInstance(null, DebugPlugin.getDefault().getLaunchManager().generateUniqueLaunchConfigurationNameFrom(className)); 
			wc.setAttribute(LayeredApplicationMainTab.PROJECT_NAME, project.getName());
			wc.setAttribute(LayeredApplicationMainTab.MAIN_CLASS, className);
			wc.setMappedResources(new IResource[] {project});
			config = wc.doSave();		
		} catch (CoreException e) {
			e.printStackTrace();			
		}
		return config;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.debug.ui.launcher.JavaLaunchShortcut#getConfigurationType()
	 */
	protected ILaunchConfigurationType getConfigurationType() {
		ILaunchManager lm = DebugPlugin.getDefault().getLaunchManager();
		return lm.getLaunchConfigurationType(ID_LAYERED_APPLICATION);		
	}

//    protected void launch(IType type, String mode) {
//        try {
//            ILaunchConfiguration config = findLaunchConfiguration(type, mode);
//            if (config != null) {
//            	config.launch(mode, null);
//            }
//        } catch (CoreException e) {
//            e.printStackTrace();
//        }
//    }

}
