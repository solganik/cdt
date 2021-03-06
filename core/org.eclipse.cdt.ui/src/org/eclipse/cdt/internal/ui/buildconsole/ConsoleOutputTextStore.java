/*******************************************************************************
 * Copyright (c) 2002, 2011 QNX Software Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * QNX Software Systems - Initial API and implementation
 *******************************************************************************/
package org.eclipse.cdt.internal.ui.buildconsole;

import org.eclipse.jface.text.ITextStore;

public class ConsoleOutputTextStore implements ITextStore {

	private StringBuffer fBuffer;

	public ConsoleOutputTextStore(int bufferSize) {
		fBuffer = new StringBuffer(bufferSize);
	}

	/**
	 * @see ITextStore#get(int)
	 */
	@Override
	public char get(int pos) {
		return fBuffer.charAt(pos);
	}

	/**
	 * @see ITextStore#get(int, int)
	 */
	@Override
	public String get(int pos, int length) {
		return fBuffer.substring(pos, pos + length);
	}

	/**
	 * @see ITextStore#getLength()
	 */
	@Override
	public int getLength() {
		return fBuffer.length();
	}

	/**
	 * @see ITextStore#replace(int, int, String)
	 */
	@Override
	public void replace(int pos, int length, String text) {
		if (text == null) {
			text = ""; //$NON-NLS-1$
		}
		fBuffer.replace(pos, pos + length, text);
	}

	/**
	 * @see ITextStore#set(String)
	 */
	@Override
	public void set(String text) {
		fBuffer = new StringBuffer(text);
	}

	/**
	 * @see StringBuffer#ensureCapacity(int)
	 */
	public void setMinimalBufferSize(int bufferSize) {
		fBuffer.ensureCapacity(bufferSize);
	}
}
