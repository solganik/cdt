/*
 *(c) Copyright QNX Software Systems Ltd. 2002.
 * All Rights Reserved.
 * 
 */
package org.eclipse.cdt.debug.internal.ui.actions;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.cdt.debug.core.cdi.ICDIFormat;
import org.eclipse.cdt.debug.core.model.ICVariable;
import org.eclipse.cdt.debug.ui.CDebugUIPlugin;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.debug.core.DebugException;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;

/**
 * 
 * Enter type comment.
 * 
 * @since Dec 16, 2002
 */
public class VariableFormatActionDelegate implements IObjectActionDelegate
{
	private int fFormat = ICDIFormat.NATURAL;
	private ICVariable[] fVariables = null;

	/**
	 * Constructor for VariableFormatActionDelegate.
	 */
	public VariableFormatActionDelegate( int format )
	{
		fFormat = format;
	}

	/**
	 * @see org.eclipse.ui.IObjectActionDelegate#setActivePart(IAction, IWorkbenchPart)
	 */
	public void setActivePart( IAction action, IWorkbenchPart targetPart )
	{
	}

	/**
	 * @see org.eclipse.ui.IActionDelegate#run(IAction)
	 */
	public void run( IAction action )
	{
		if ( fVariables != null && fVariables.length > 0 )
		{
			final MultiStatus ms = new MultiStatus( CDebugUIPlugin.getUniqueIdentifier(), 
													DebugException.REQUEST_FAILED, "", null ); 
			BusyIndicator.showWhile( Display.getCurrent(), 
									new Runnable()
										{
											public void run()
											{
												try
												{
													doAction( fVariables );
												}
												catch( DebugException e )
												{
													ms.merge( e.getStatus() );
												}
											}
										} );
			if ( !ms.isOK() )
			{
				IWorkbenchWindow window = CDebugUIPlugin.getActiveWorkbenchWindow();
				if ( window != null )
				{
					CDebugUIPlugin.errorDialog( "Unable to set format of variable.", ms );
				}
				else
				{
					CDebugUIPlugin.log( ms );
				}
			}

		}
	}

	/**
	 * @see org.eclipse.ui.IActionDelegate#selectionChanged(IAction, ISelection)
	 */
	public void selectionChanged( IAction action, ISelection selection )
	{
		if ( selection instanceof IStructuredSelection )
		{
			List list = new ArrayList();
			IStructuredSelection ssel = (IStructuredSelection)selection;
			Iterator i = ssel.iterator();
			while ( i.hasNext() )
			{
				Object o = i.next();
				if ( o instanceof ICVariable )
				{
					ICVariable var = (ICVariable)o;
					boolean enabled = var.isEditable();
					action.setEnabled( enabled );
					if ( enabled )
					{
						action.setChecked( var.getFormat() == fFormat  );
						list.add(o);
					}
				}
			}
			fVariables = new ICVariable[list.size()];
			list.toArray(fVariables);
		} else {
			action.setChecked( false );
			action.setEnabled( false );
		}
	}
	
	protected void doAction( ICVariable[] vars ) throws DebugException
	{
		for (int i = 0; i < vars.length; i++ )
		{
			vars[i].setFormat(fFormat);
		}
	}
}
