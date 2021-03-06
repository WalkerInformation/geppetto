/**
 * Copyright (c) 2011 Cloudsmith Inc. and other contributors, as listed below.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *   Cloudsmith
 * 
 */
package org.cloudsmith.geppetto.ui.editor;

import static org.cloudsmith.geppetto.forge.Forge.METADATA_JSON_NAME;
import static org.cloudsmith.geppetto.forge.Forge.MODULEFILE_NAME;

import java.util.Iterator;

import org.cloudsmith.geppetto.diagnostic.Diagnostic;
import org.cloudsmith.geppetto.forge.Forge;
import org.cloudsmith.geppetto.pp.dsl.ui.PPUiConstants;
import org.cloudsmith.geppetto.ui.UIPlugin;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IShowEditorInput;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.editors.text.TextEditor;
import org.eclipse.ui.forms.editor.FormEditor;
import org.eclipse.ui.ide.IGotoMarker;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.MarkerAnnotation;

import com.google.inject.Inject;

public class ModuleMetadataEditor extends FormEditor implements IGotoMarker, IShowEditorInput, IResourceChangeListener {

	static class DiagnosticAnnotation extends Annotation {
		private static final String INFO_TYPE = "org.eclipse.ui.workbench.texteditor.info"; //$NON-NLS-1$

		private static final String ERROR_TYPE = "org.eclipse.ui.workbench.texteditor.error"; //$NON-NLS-1$

		private static final String WARNING_TYPE = "org.eclipse.ui.workbench.texteditor.warning"; //$NON-NLS-1$

		static void add(Diagnostic diag, IAnnotationModel model, IDocument text) {
			IRegion region;
			Position position;
			try {
				int line = 0;
				if(diag.getLineNumber() > 0)
					line = diag.getLineNumber() - 1;
				region = text.getLineInformation(line);
				position = new Position(region.getOffset(), region.getLength());
			}
			catch(BadLocationException e) {
				position = new Position(0, text.getLength());
			}
			model.addAnnotation(new DiagnosticAnnotation(diag), position);
		}

		public static void clearBuilderAnnotations(IAnnotationModel model) {
			Iterator<?> iter = model.getAnnotationIterator();
			while(iter.hasNext()) {
				Object a = iter.next();
				if(a instanceof MarkerAnnotation) {
					try {
						// Remove this marker from the transient document model. It stems from the persisted content
						// and may since have been corrected. If not, it will be added as a DiagnosticAnnotation
						if(((MarkerAnnotation) a).getMarker().getType().equals(
							PPUiConstants.PUPPET_MODULE_PROBLEM_MARKER_TYPE))
							model.removeAnnotation((Annotation) a);
					}
					catch(CoreException e) {
					}
				}
			}
		}

		public static void clearDiagnosticAnnotations(IAnnotationModel model) {
			Iterator<?> iter = model.getAnnotationIterator();
			while(iter.hasNext()) {
				Object a = iter.next();
				if(a instanceof DiagnosticAnnotation)
					model.removeAnnotation((Annotation) a);
			}
		}

		static String getAnnotationType(Diagnostic diag) {
			switch(diag.getSeverity()) {
				case Diagnostic.FATAL:
				case Diagnostic.ERROR:
					return ERROR_TYPE;
				case Diagnostic.WARNING:
					return WARNING_TYPE;
			}
			return INFO_TYPE;
		}

		DiagnosticAnnotation(Diagnostic diag) {
			super(getAnnotationType(diag), false, diag.getMessage());
		}
	}

	@Inject
	private Forge forge;

	private ModuleOverviewPage overviewPage;

	private ModuleDependenciesPage dependenciesPage;

	private ModuleSourcePage sourcePage;

	private TextEditor derivedJSON;

	private boolean stale = false;

	private final MetadataModel model = new MetadataModel();

	@Override
	protected void addPages() {
		try {
			overviewPage = new ModuleOverviewPage(this, "overview", UIPlugin.INSTANCE.getString("_UI_Overview_title")); //$NON-NLS-1$ //$NON-NLS-2$
			addPage(overviewPage);

			dependenciesPage = new ModuleDependenciesPage(
				this, "dependencies", UIPlugin.INSTANCE.getString("_UI_Dependencies_title")); //$NON-NLS-1$ //$NON-NLS-2$
			addPage(dependenciesPage);

			sourcePage = new ModuleSourcePage(this);
			int sourcePageIdx = addPage(sourcePage, getEditorInput());
			setPageText(sourcePageIdx, UIPlugin.INSTANCE.getString("_UI_Source_title"));
			refreshModel();
			sourcePage.initialize();

			String name = getModuleName();
			if(name != null)
				setPartName(name);

			IFile file = getFile();
			if(file == null) {
				setPageText(sourcePageIdx, UIPlugin.INSTANCE.getString("_UI_Source_title"));
			}
			else if(MODULEFILE_NAME.equals(file.getName())) {
				setPageText(sourcePageIdx, UIPlugin.INSTANCE.getString("_UI_Source_title"));
				IFile metadataJSON = file.getParent().getFile(Path.fromPortableString(METADATA_JSON_NAME));
				if(metadataJSON.exists() && metadataJSON.isDerived()) {
					FileEditorInput metadataInput = new FileEditorInput(metadataJSON);
					derivedJSON = new TextEditor() {
						@Override
						public boolean isEditable() {
							return false;
						}
					};
					setPageText(
						addPage(derivedJSON, metadataInput), UIPlugin.INSTANCE.getString("_UI_JSON_Derived_title"));
				}
			}
			else
				setPageText(sourcePageIdx, UIPlugin.INSTANCE.getString("_UI_JSON_title"));
		}
		catch(Exception e) {
			UIPlugin.INSTANCE.log(e);
		}
	}

	@Override
	public void dispose() {
		ResourcesPlugin.getWorkspace().removeResourceChangeListener(this);
		super.dispose();
	}

	@Override
	public void doSave(IProgressMonitor monitor) {
		sourcePage.doSave(monitor);
		String name = getModuleName();
		if(name != null)
			setPartName(name);
	}

	@Override
	public void doSaveAs() {
		// do nothing
	}

	ModuleDependenciesPage getDependenciesPage() {
		return dependenciesPage;
	}

	IDocument getDocument() {
		if(sourcePage != null) {
			IDocumentProvider dp = sourcePage.getDocumentProvider();
			if(dp != null)
				return dp.getDocument(getEditorInput());
		}
		return null;
	}

	IFile getFile() {
		IEditorInput input = getEditorInput();
		return input == null
				? null
				: (IFile) input.getAdapter(IFile.class);
	}

	Forge getForge() {
		return forge;
	}

	MetadataModel getModel() {
		if(stale)
			refreshModel();
		return model;
	}

	String getModuleName() {
		return getModel().getModuleName();
	}

	ModuleOverviewPage getOverviewPage() {
		return overviewPage;
	}

	IPath getPath() {
		IFile currentFile = getFile();
		return currentFile == null
				? null
				: currentFile.getLocation();
	}

	ModuleSourcePage getSourcePage() {
		return sourcePage;
	}

	@Override
	public void gotoMarker(IMarker marker) {
		int line = marker.getAttribute(IMarker.LINE_NUMBER, 0);
		if(line > 0) {
			((IGotoMarker) sourcePage.getAdapter(IGotoMarker.class)).gotoMarker(marker);
			setActiveEditor(sourcePage);
		}
	}

	@Override
	public void init(IEditorSite site, IEditorInput input) throws PartInitException {
		if(input instanceof IFileEditorInput) {
			IFile file = ((IFileEditorInput) input).getFile();
			if(file.isDerived() && METADATA_JSON_NAME.equals(file.getName())) {
				// We prefer to open the Modulefile editor here. It will show the
				// derived JSON content in a read-only tab
				IFile moduleFile = file.getParent().getFile(Path.fromPortableString(MODULEFILE_NAME));
				if(moduleFile.exists())
					input = new FileEditorInput(moduleFile);
			}

			// We want to listen to build events since they might change our conditions (dependencies
			// might come and go for instance).
			file.getWorkspace().addResourceChangeListener(this, IResourceChangeEvent.POST_BUILD);
		}
		super.init(site, input);
	}

	@Override
	public boolean isSaveAsAllowed() {
		return false;
	}

	void markStale() {
		stale = true;
		overviewPage.markStale();
		dependenciesPage.markStale();
	}

	private void refreshModel() {
		Diagnostic chain = new Diagnostic();
		IPath path = getPath();
		if(path == null)
			return;
		model.setDocument(getDocument(), path, chain);
		sourcePage.updateDiagnosticAnnotations(chain);
		stale = false;
	}

	@Override
	public void resourceChanged(IResourceChangeEvent event) {
		if(event.getType() == IResourceChangeEvent.POST_BUILD) {
			// Ensure that our markers is as aligned as they can possibly be
			// with the current state of the workspace
			IFile file = getFile();
			if(file != null && file.getProject() == event.getSource()) {
				markStale();
				refreshModel();
			}
		}
	}

	@Override
	public void showEditorInput(IEditorInput input) {
		if(input instanceof IFileEditorInput) {
			IFile file = ((IFileEditorInput) input).getFile();
			if(file.isDerived()) {
				if(derivedJSON != null && METADATA_JSON_NAME.equals(file.getName()))
					setActiveEditor(derivedJSON);
			}
			else if(derivedJSON == getActiveEditor())
				setActivePage(overviewPage.getIndex());
		}
	}
}
