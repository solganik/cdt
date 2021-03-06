/*******************************************************************************
 * Copyright (c) 2016 QNX Software Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.cdt.internal.core.build;

import org.eclipse.cdt.core.CCorePlugin;
import org.eclipse.cdt.core.build.ICBuildConfiguration;
import org.eclipse.cdt.core.build.ICBuildConfigurationManager;
import org.eclipse.core.resources.IBuildConfiguration;
import org.eclipse.core.runtime.IAdapterFactory;

public class CBuildConfigAdapterFactory implements IAdapterFactory {

	private ICBuildConfigurationManager manager = CCorePlugin.getService(ICBuildConfigurationManager.class);

	@SuppressWarnings("unchecked")
	@Override
	public <T> T getAdapter(Object adaptableObject, Class<T> adapterType) {
		if (ICBuildConfiguration.class.equals(adapterType)
				&& adaptableObject instanceof IBuildConfiguration) {
			IBuildConfiguration config = (IBuildConfiguration) adaptableObject;
			return (T) manager.getBuildConfiguration(config);
		}
		return null;
	}

	@Override
	public Class<?>[] getAdapterList() {
		return new Class<?>[] { ICBuildConfiguration.class };
	}

}
