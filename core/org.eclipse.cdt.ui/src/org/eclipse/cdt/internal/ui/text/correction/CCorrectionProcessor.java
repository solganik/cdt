/*******************************************************************************
 * Copyright (c) 2000, 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 * 	   Sergey Prigogin (Google)
 *******************************************************************************/

package org.eclipse.cdt.internal.ui.text.correction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.ISafeRunnable;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.SafeRunner;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.contentassist.ContentAssistEvent;
import org.eclipse.jface.text.contentassist.ICompletionListener;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.quickassist.IQuickAssistInvocationContext;
import org.eclipse.jface.text.quickassist.IQuickAssistProcessor;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IMarkerHelpRegistry;
import org.eclipse.ui.IMarkerResolution;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.texteditor.SimpleMarkerAnnotation;

import org.eclipse.cdt.core.model.ITranslationUnit;
import org.eclipse.cdt.ui.CUIPlugin;
import org.eclipse.cdt.ui.text.ICCompletionProposal;
import org.eclipse.cdt.ui.text.IProblemLocation;
import org.eclipse.cdt.ui.text.IQuickFixProcessor;

import org.eclipse.cdt.internal.ui.editor.ICAnnotation;
import org.eclipse.cdt.internal.ui.text.contentassist.CCompletionProposal;
import org.eclipse.cdt.internal.ui.text.contentassist.CCompletionProposalComparator;

public class CCorrectionProcessor implements IQuickAssistProcessor {
	private static final String QUICKFIX_PROCESSOR_CONTRIBUTION_ID= "quickFixProcessors"; //$NON-NLS-1$
	private static final String QUICKASSIST_PROCESSOR_CONTRIBUTION_ID= "quickAssistProcessors"; //$NON-NLS-1$

	private static ContributedProcessorDescriptor[] fgContributedAssistProcessors= null;
	private static ContributedProcessorDescriptor[] fgContributedCorrectionProcessors= null;

	private static ContributedProcessorDescriptor[] getProcessorDescriptors(String contributionId, boolean testMarkerTypes) {
		IConfigurationElement[] elements= Platform.getExtensionRegistry().getConfigurationElementsFor(CUIPlugin.PLUGIN_ID, contributionId);
		ArrayList res= new ArrayList(elements.length);

		for (int i= 0; i < elements.length; i++) {
			ContributedProcessorDescriptor desc= new ContributedProcessorDescriptor(elements[i], testMarkerTypes);
			IStatus status= desc.checkSyntax();
			if (status.isOK()) {
				res.add(desc);
			} else {
				CUIPlugin.getDefault().log(status);
			}
		}
		return (ContributedProcessorDescriptor[]) res.toArray(new ContributedProcessorDescriptor[res.size()]);
	}

	private static ContributedProcessorDescriptor[] getCorrectionProcessors() {
		if (fgContributedCorrectionProcessors == null) {
			fgContributedCorrectionProcessors= getProcessorDescriptors(QUICKFIX_PROCESSOR_CONTRIBUTION_ID, true);
		}
		return fgContributedCorrectionProcessors;
	}

	private static ContributedProcessorDescriptor[] getAssistProcessors() {
		if (fgContributedAssistProcessors == null) {
			fgContributedAssistProcessors= getProcessorDescriptors(QUICKASSIST_PROCESSOR_CONTRIBUTION_ID, false);
		}
		return fgContributedAssistProcessors;
	}

	public static boolean hasCorrections(ITranslationUnit cu, int problemId, String markerType) {
		ContributedProcessorDescriptor[] processors= getCorrectionProcessors();
		SafeHasCorrections collector= new SafeHasCorrections(cu, problemId);
		for (int i= 0; i < processors.length; i++) {
			if (processors[i].canHandleMarkerType(markerType)) {
				collector.process(processors[i]);
				if (collector.hasCorrections()) {
					return true;
				}
			}
		}
		return false;
	}

	public static boolean isQuickFixableType(Annotation annotation) {
		return (annotation instanceof ICAnnotation || annotation instanceof SimpleMarkerAnnotation) && !annotation.isMarkedDeleted();
	}

	public static boolean hasCorrections(Annotation annotation) {
		if (annotation instanceof ICAnnotation) {
			ICAnnotation cAnnotation= (ICAnnotation) annotation;
			int problemId= cAnnotation.getId();
			if (problemId != -1) {
				ITranslationUnit cu= cAnnotation.getTranslationUnit();
				if (cu != null) {
					return hasCorrections(cu, problemId, cAnnotation.getMarkerType());
				}
			}
		}
		if (annotation instanceof SimpleMarkerAnnotation) {
			return hasCorrections(((SimpleMarkerAnnotation) annotation).getMarker());
		}
		return false;
	}

	private static boolean hasCorrections(IMarker marker) {
		if (marker == null || !marker.exists())
			return false;

		IMarkerHelpRegistry registry= IDE.getMarkerHelpRegistry();
		return registry != null && registry.hasResolutions(marker);
	}

	public static boolean hasAssists(CorrectionContext context) {
		ContributedProcessorDescriptor[] processors= getAssistProcessors();
		SafeHasAssist collector= new SafeHasAssist(context);

		for (int i= 0; i < processors.length; i++) {
			collector.process(processors[i]);
			if (collector.hasAssists()) {
				return true;
			}
		}
		return false;
	}

	private CCorrectionAssistant fAssistant;
	private String fErrorMessage;

	/*
	 * Constructor for CCorrectionProcessor.
	 */
	public CCorrectionProcessor(CCorrectionAssistant assistant) {
		fAssistant= assistant;
		fAssistant.addCompletionListener(new ICompletionListener() {
		
			public void assistSessionEnded(ContentAssistEvent event) {
				fAssistant.setStatusLineVisible(false);
			}
		
			public void assistSessionStarted(ContentAssistEvent event) {
				fAssistant.setStatusLineVisible(true);
			}

			public void selectionChanged(ICompletionProposal proposal, boolean smartToggle) {
				if (proposal instanceof IStatusLineProposal) {
					IStatusLineProposal statusLineProposal= (IStatusLineProposal)proposal;
					String message= statusLineProposal.getStatusMessage();
					if (message != null) {
						fAssistant.setStatusMessage(message);
					} else {
						fAssistant.setStatusMessage(""); //$NON-NLS-1$
					}
				} else {
					fAssistant.setStatusMessage(""); //$NON-NLS-1$
				}
			}
		});
	}

	/*
	 * @see IContentAssistProcessor#computeCompletionProposals(ITextViewer, int)
	 */
	public ICompletionProposal[] computeQuickAssistProposals(IQuickAssistInvocationContext quickAssistContext) {
		ISourceViewer viewer= quickAssistContext.getSourceViewer();
		int documentOffset= quickAssistContext.getOffset();
		
		IEditorPart part= fAssistant.getEditor();

		ITranslationUnit cu= CUIPlugin.getDefault().getWorkingCopyManager().getWorkingCopy(part.getEditorInput());
		IAnnotationModel model= CUIPlugin.getDefault().getDocumentProvider().getAnnotationModel(part.getEditorInput());
		
		int length= viewer != null ? viewer.getSelectedRange().y : 0;
		CorrectionContext context= new CorrectionContext(cu, viewer, documentOffset, length);

		Annotation[] annotations= fAssistant.getAnnotationsAtOffset();
		
		fErrorMessage= null;
		
		ICompletionProposal[] res= null;
		if (model != null && annotations != null) {
			ArrayList proposals= new ArrayList(10);
			IStatus status= collectProposals(context, model, annotations, true, !fAssistant.isUpdatedOffset(), proposals);
			res= (ICompletionProposal[]) proposals.toArray(new ICompletionProposal[proposals.size()]);
			if (!status.isOK()) {
				fErrorMessage= status.getMessage();
				CUIPlugin.getDefault().log(status);
			}
		}
		
		if (res == null || res.length == 0) {
			return new ICompletionProposal[]
					{ new CCompletionProposal("", 0, 0, null, CorrectionMessages.NoCorrectionProposal_description, 0) }; //$NON-NLS-1$
		}
		if (res.length > 1) {
			Arrays.sort(res, new CCompletionProposalComparator());
		}
		return res;
	}

	public static IStatus collectProposals(CorrectionContext context, IAnnotationModel model, Annotation[] annotations, boolean addQuickFixes, boolean addQuickAssists, Collection proposals) {
		ArrayList problems= new ArrayList();
		
		// collect problem locations and corrections from marker annotations
		for (int i= 0; i < annotations.length; i++) {
			Annotation curr= annotations[i];
			if (curr instanceof ICAnnotation) {
				ProblemLocation problemLocation= getProblemLocation((ICAnnotation) curr, model);
				if (problemLocation != null) {
					problems.add(problemLocation);
				}
			} else if (addQuickFixes && curr instanceof SimpleMarkerAnnotation) {
				// don't collect if annotation is already a C annotation
				collectMarkerProposals((SimpleMarkerAnnotation) curr, proposals);
			}
		}
		MultiStatus resStatus= null;
		
		IProblemLocation[] problemLocations= (IProblemLocation[]) problems.toArray(new IProblemLocation[problems.size()]);
		if (addQuickFixes) {
			IStatus status= collectCorrections(context, problemLocations, proposals);
			if (!status.isOK()) {
				resStatus= new MultiStatus(CUIPlugin.PLUGIN_ID, IStatus.ERROR, CorrectionMessages.CCorrectionProcessor_error_quickfix_message, null);
				resStatus.add(status);
			}
		}
		if (addQuickAssists) {
			IStatus status= collectAssists(context, problemLocations, proposals);
			if (!status.isOK()) {
				if (resStatus == null) {
					resStatus= new MultiStatus(CUIPlugin.PLUGIN_ID, IStatus.ERROR, CorrectionMessages.CCorrectionProcessor_error_quickassist_message, null);
				}
				resStatus.add(status);
			}
		}
		if (resStatus != null) {
			return resStatus;
		}
		return Status.OK_STATUS;
	}
	
	private static ProblemLocation getProblemLocation(ICAnnotation cAnnotation, IAnnotationModel model) {
		int problemId= cAnnotation.getId();
		if (problemId != -1) {
			Position pos= model.getPosition((Annotation) cAnnotation);
			if (pos != null) {
				return new ProblemLocation(pos.getOffset(), pos.getLength(), cAnnotation); // java problems all handled by the quick assist processors
			}
		}
		return null;
	}

	private static void collectMarkerProposals(SimpleMarkerAnnotation annotation, Collection proposals) {
		IMarker marker= annotation.getMarker();
		IMarkerResolution[] res= IDE.getMarkerHelpRegistry().getResolutions(marker);
		if (res.length > 0) {
			for (int i= 0; i < res.length; i++) {
				proposals.add(new MarkerResolutionProposal(res[i], marker));
			}
		}
	}

	private static abstract class SafeCorrectionProcessorAccess implements ISafeRunnable {
		private MultiStatus fMulti= null;
		private ContributedProcessorDescriptor fDescriptor;

		public void process(ContributedProcessorDescriptor[] desc) {
			for (int i= 0; i < desc.length; i++) {
				fDescriptor= desc[i];
				SafeRunner.run(this);
			}
		}

		public void process(ContributedProcessorDescriptor desc) {
			fDescriptor= desc;
			SafeRunner.run(this);
		}

		public void run() throws Exception {
			safeRun(fDescriptor);
		}

		protected abstract void safeRun(ContributedProcessorDescriptor processor) throws Exception;

		public void handleException(Throwable exception) {
			if (fMulti == null) {
				fMulti= new MultiStatus(CUIPlugin.PLUGIN_ID, IStatus.OK, CorrectionMessages.CCorrectionProcessor_error_status, null);
			}
			fMulti.merge(new Status(IStatus.ERROR, CUIPlugin.PLUGIN_ID, IStatus.ERROR, CorrectionMessages.CCorrectionProcessor_error_status, exception));
		}

		public IStatus getStatus() {
			if (fMulti == null) {
				return Status.OK_STATUS;
			}
			return fMulti;
		}
	}

	private static class SafeCorrectionCollector extends SafeCorrectionProcessorAccess {
		private final CorrectionContext fContext;
		private final Collection fProposals;
		private IProblemLocation[] fLocations;

		public SafeCorrectionCollector(CorrectionContext context, Collection proposals) {
			fContext= context;
			fProposals= proposals;
		}
		
		public void setProblemLocations(IProblemLocation[] locations) {
			fLocations= locations;
		}

		public void safeRun(ContributedProcessorDescriptor desc) throws Exception {
			IQuickFixProcessor curr= (IQuickFixProcessor) desc.getProcessor(fContext.getTranslationUnit());
			if (curr != null) {
				ICCompletionProposal[] res= curr.getCorrections(fContext, fLocations);
				if (res != null) {
					for (int k= 0; k < res.length; k++) {
						fProposals.add(res[k]);
					}
				}
			}
		}
	}

	private static class SafeAssistCollector extends SafeCorrectionProcessorAccess {
		private final CorrectionContext fContext;
		private final IProblemLocation[] fLocations;
		private final Collection fProposals;

		public SafeAssistCollector(CorrectionContext context, IProblemLocation[] locations, Collection proposals) {
			fContext= context;
			fLocations= locations;
			fProposals= proposals;
		}

		public void safeRun(ContributedProcessorDescriptor desc) throws Exception {
			IQuickFixProcessor curr= (IQuickFixProcessor) desc.getProcessor(fContext.getTranslationUnit());
			if (curr != null) {
				ICCompletionProposal[] res= curr.getCorrections(fContext, fLocations);
				if (res != null) {
					for (int k= 0; k < res.length; k++) {
						fProposals.add(res[k]);
					}
				}
			}
		}
	}

	private static class SafeHasAssist extends SafeCorrectionProcessorAccess {
		private final CorrectionContext fContext;
		private boolean fHasAssists;

		public SafeHasAssist(CorrectionContext context) {
			fContext= context;
			fHasAssists= false;
		}

		public boolean hasAssists() {
			return fHasAssists;
		}

		public void safeRun(ContributedProcessorDescriptor desc) throws Exception {
			IQuickAssistProcessor processor= (IQuickAssistProcessor) desc.getProcessor(fContext.getTranslationUnit());
			if (processor != null && processor.canAssist(fContext)) {
				fHasAssists= true;
			}
		}
	}

	private static class SafeHasCorrections extends SafeCorrectionProcessorAccess {
		private final ITranslationUnit fCu;
		private final int fProblemId;
		private boolean fHasCorrections;

		public SafeHasCorrections(ITranslationUnit cu, int problemId) {
			fCu= cu;
			fProblemId= problemId;
			fHasCorrections= false;
		}

		public boolean hasCorrections() {
			return fHasCorrections;
		}

		public void safeRun(ContributedProcessorDescriptor desc) throws Exception {
			IQuickFixProcessor processor= (IQuickFixProcessor) desc.getProcessor(fCu);
			if (processor != null && processor.hasCorrections(fCu, fProblemId)) {
				fHasCorrections= true;
			}
		}
	}

	public static IStatus collectCorrections(CorrectionContext context, IProblemLocation[] locations, Collection proposals) {
		ContributedProcessorDescriptor[] processors= getCorrectionProcessors();
		SafeCorrectionCollector collector= new SafeCorrectionCollector(context, proposals);
		for (int i= 0; i < processors.length; i++) {
			ContributedProcessorDescriptor curr= processors[i];
			IProblemLocation[] handled= getHandledProblems(locations, curr);
			if (handled != null) {
				collector.setProblemLocations(handled);
				collector.process(curr);
			}
		}
		return collector.getStatus();
	}

	private static IProblemLocation[] getHandledProblems(IProblemLocation[] locations, ContributedProcessorDescriptor processor) {
		// implementation tries to avoid creating a new array
		boolean allHandled= true;
		ArrayList res= null;
		for (int i= 0; i < locations.length; i++) {
			IProblemLocation curr= locations[i];
			if (processor.canHandleMarkerType(curr.getMarkerType())) {
				if (!allHandled) { // first handled problem
					if (res == null) {
						res= new ArrayList(locations.length - i);
					}
					res.add(curr);
				}
			} else if (allHandled) { 
				if (i > 0) { // first non handled problem 
					res= new ArrayList(locations.length - i);
					for (int k= 0; k < i; k++) {
						res.add(locations[k]);
					}
				}
				allHandled= false;
			}
		}
		if (allHandled) {
			return locations;
		}
		if (res == null) {
			return null;
		}
		return (IProblemLocation[]) res.toArray(new IProblemLocation[res.size()]);
	}

	public static IStatus collectAssists(CorrectionContext context, IProblemLocation[] locations, Collection proposals) {
		ContributedProcessorDescriptor[] processors= getAssistProcessors();
		SafeAssistCollector collector= new SafeAssistCollector(context, locations, proposals);
		collector.process(processors);

		return collector.getStatus();
	}

	/*
	 * @see IContentAssistProcessor#getErrorMessage()
	 */
	public String getErrorMessage() {
		return fErrorMessage;
	}

	/*
	 * @see org.eclipse.jface.text.quickassist.IQuickAssistProcessor#canFix(org.eclipse.jface.text.source.Annotation)
	 */
	public boolean canFix(Annotation annotation) {
		return hasCorrections(annotation);
	}

	/*
	 * @see org.eclipse.jface.text.quickassist.IQuickAssistProcessor#canAssist(org.eclipse.jface.text.quickassist.IQuickAssistInvocationContext)
	 */
	public boolean canAssist(IQuickAssistInvocationContext invocationContext) {
		if (invocationContext instanceof CorrectionContext)
			return hasAssists((CorrectionContext) invocationContext);
		return false;
	}
}
