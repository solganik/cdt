/*******************************************************************************
 * Copyright (c) 2015 QNX Software Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * QNX Software Systems - Initial API and implementation
 *******************************************************************************/
package org.eclipse.cdt.qt.core.qmldir;

/**
 * An AST Node representing a Resource Command in a qmldir file.
 *
 * @see <a href="http://doc.qt.io/qt-5/qtqml-modules-qmldir.html">Module Definition qmldir Files</a>
 */
public interface IQDirResourceCommand extends IQDirCommand {
	/**
	 * Gets the <code>IQDirWord</code> representing the identifier of the resource.
	 *
	 * @return the identifier of the resource
	 */
	public IQDirWord getResourceIdentifier();

	/**
	 * Gets the <code>IQDirVersion</code> representing the initial version of the resource.
	 *
	 * @return the initial version
	 */
	public IQDirVersion getInitialVersion();

	/**
	 * Gets the <code>IQDirWord</code> representing the filename of the resource.
	 *
	 * @return the filename
	 */
	public IQDirWord getFile();
}
