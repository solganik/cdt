/*******************************************************************************
 * Copyright (c) 2015 QNX Software Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     QNX Software Systems - Initial API and implementation
 *******************************************************************************/
package org.eclipse.cdt.arduino.core.internal.launch;

import java.io.IOException;

import org.eclipse.cdt.arduino.core.internal.Activator;
import org.eclipse.cdt.arduino.core.internal.Messages;
import org.eclipse.cdt.arduino.core.internal.build.ArduinoBuildConfiguration;
import org.eclipse.cdt.arduino.core.internal.remote.ArduinoRemoteConnection;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.launchbar.core.target.ILaunchTarget;
import org.eclipse.launchbar.core.target.launch.ITargetedLaunch;
import org.eclipse.launchbar.core.target.launch.LaunchConfigurationTargetedDelegate;
import org.eclipse.remote.core.IRemoteConnection;

public class ArduinoLaunchConfigurationDelegate extends LaunchConfigurationTargetedDelegate {

	public static final String TYPE_ID = "org.eclipse.cdt.arduino.core.launchConfigurationType"; //$NON-NLS-1$
	public static final String CONNECTION_NAME = Activator.getId() + ".connectionName"; //$NON-NLS-1$

	@Override
	public ITargetedLaunch getLaunch(ILaunchConfiguration configuration, String mode, ILaunchTarget target)
			throws CoreException {
		return new ArduinoLaunch(configuration, mode, null, target);
	}

	@Override
	public boolean buildForLaunch(ILaunchConfiguration configuration, String mode, ILaunchTarget target,
			IProgressMonitor monitor) throws CoreException {
		IRemoteConnection connection = target.getAdapter(IRemoteConnection.class);
		if (target != null) {
			ArduinoRemoteConnection arduinoTarget = connection.getService(ArduinoRemoteConnection.class);

			// 1. make sure proper build config is set active
			IProject project = configuration.getMappedResources()[0].getProject();
			ArduinoBuildConfiguration arduinoConfig = ArduinoBuildConfiguration.getConfig(project, arduinoTarget,
					monitor);
			arduinoConfig.setActive(monitor);
		}

		// 2. Run the build
		return super.buildForLaunch(configuration, mode, target, monitor);
	}

	@Override
	protected IProject[] getBuildOrder(ILaunchConfiguration configuration, String mode) throws CoreException {
		// 1. Extract project from configuration
		IProject project = configuration.getMappedResources()[0].getProject();
		return new IProject[] { project };
	}

	@Override
	public void launch(final ILaunchConfiguration configuration, String mode, final ILaunch launch,
			IProgressMonitor monitor) throws CoreException {
		try {
			ILaunchTarget target = ((ITargetedLaunch) launch).getLaunchTarget();
			IRemoteConnection connection = target.getAdapter(IRemoteConnection.class);
			if (connection == null) {
				throw new CoreException(
						new Status(IStatus.ERROR, Activator.getId(), Messages.ArduinoLaunchConfigurationDelegate_2));
			}
			ArduinoRemoteConnection arduinoTarget = connection.getService(ArduinoRemoteConnection.class);

			// The project
			IProject project = (IProject) configuration.getMappedResources()[0];

			// The build config
			ArduinoBuildConfiguration arduinoConfig = ArduinoBuildConfiguration.getConfig(project, arduinoTarget,
					monitor);
			String[] uploadCmd = arduinoConfig.getUploadCommand(arduinoTarget.getPortName());

			StringBuffer cmdStr = new StringBuffer(uploadCmd[0]);
			for (int i = 1; i < uploadCmd.length; ++i) {
				cmdStr.append(' ');
				cmdStr.append(uploadCmd[i]);
			}
			// Start the launch
			((ArduinoLaunch) launch).start();

			// Run the process and capture the results in the console
			ProcessBuilder processBuilder = new ProcessBuilder(uploadCmd).directory(arduinoConfig.getBuildDirectory());
			arduinoConfig.setEnvironment(processBuilder.environment());
			Process process = processBuilder.start();
			DebugPlugin.newProcess(launch, process, cmdStr.toString());
		} catch (IOException e) {
			throw new CoreException(new Status(IStatus.ERROR, Activator.getId(), e.getLocalizedMessage(), e));
		}

	}

}
