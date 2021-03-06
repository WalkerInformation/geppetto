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
package org.cloudsmith.geppetto.ui.wizard;

import static org.cloudsmith.geppetto.common.Strings.trimToNull;
import static org.cloudsmith.geppetto.forge.Forge.METADATA_JSON_NAME;
import static org.cloudsmith.geppetto.forge.Forge.MODULEFILE_NAME;
import static org.cloudsmith.geppetto.ui.UIPlugin.getLocalString;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.net.SocketException;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.cloudsmith.geppetto.diagnostic.Diagnostic;
import org.cloudsmith.geppetto.forge.util.ModuleUtils;
import org.cloudsmith.geppetto.forge.v1.model.ModuleInfo;
import org.cloudsmith.geppetto.forge.v1.service.ModuleService;
import org.cloudsmith.geppetto.forge.v2.model.Metadata;
import org.cloudsmith.geppetto.semver.VersionRange;
import org.cloudsmith.geppetto.ui.UIPlugin;
import org.cloudsmith.geppetto.ui.dialog.ModuleListSelectionDialog;
import org.cloudsmith.geppetto.ui.util.ResourceUtil;
import org.cloudsmith.geppetto.ui.util.StringUtil;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.emf.edit.ui.provider.ExtendedImageRegistry;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.dialogs.WizardNewProjectCreationPage;

public class NewPuppetProjectFromForgeWizard extends NewPuppetModuleProjectWizard {

	protected class PuppetProjectFromForgeCreationPage extends NewPuppetModuleProjectWizard.PuppetProjectCreationPage {

		protected Text projectNameField;

		protected Text moduleField = null;

		protected PuppetProjectFromForgeCreationPage(String pageName) {
			super(pageName);
			setNeedsProgressMonitor(true);
		}

		@Override
		public void createControl(Composite parent) {
			parent = new Composite(parent, SWT.NONE);

			GridLayout gridLayout = new GridLayout();
			gridLayout.marginHeight = 0;
			gridLayout.marginWidth = 0;

			parent.setLayout(gridLayout);

			GridDataFactory.fillDefaults().applyTo(parent);

			Composite composite = new Composite(parent, SWT.NONE);

			gridLayout = new GridLayout(3, false);
			gridLayout.marginHeight = 10;
			gridLayout.marginWidth = 10;

			composite.setLayout(gridLayout);

			GridDataFactory.swtDefaults().align(SWT.FILL, SWT.FILL).grab(true, false).applyTo(composite);

			createModuleGroup(composite);

			super.createControl(parent);
			for(Control c1 : parent.getChildren())
				if(c1 instanceof Composite)
					for(Control c2 : ((Composite) c1).getChildren())
						if(c2 instanceof Composite)
							for(Control c3 : ((Composite) c2).getChildren())
								if(c3 instanceof Text && getProjectName().equals(((Text) c3).getText())) {
									projectNameField = (Text) c3;
									break;

								}
			setControl(parent);
		}

		protected void createModuleGroup(Composite parent) {
			Label moduleLabel = new Label(parent, SWT.NONE);
			moduleLabel.setText(getLocalString("_UI_Module_label")); //$NON-NLS-1$

			moduleField = new Text(parent, SWT.BORDER | SWT.READ_ONLY);
			moduleField.addMouseListener(new MouseAdapter() {

				@Override
				public void mouseDoubleClick(MouseEvent me) {
					promptForModuleSelection();
				}
			});

			GridDataFactory.fillDefaults().align(SWT.FILL, SWT.CENTER).grab(true, false).applyTo(moduleField);

			Button resultTypeButton = new Button(parent, SWT.PUSH);
			resultTypeButton.setText(getLocalString("_UI_Select_label")); //$NON-NLS-1$
			resultTypeButton.addSelectionListener(new SelectionAdapter() {

				@Override
				public void widgetSelected(SelectionEvent se) {
					promptForModuleSelection();
				}
			});
		}

		protected ModuleInfo[] getModuleChoices(final String keyword) {
			ModuleInfo[] zeroModules = new ModuleInfo[0];
			try {
				final ModuleInfo[][] choicesResult = new ModuleInfo[1][];
				choicesResult[0] = zeroModules;
				getContainer().run(true, true, new IRunnableWithProgress() {
					@Override
					public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
						monitor.beginTask("Fetching latest releases from the Forge", 55);
						try {
							final Exception[] te = new Exception[1];
							final ModuleService moduleService = getModuleServiceV1();
							Thread t = new Thread() {
								@Override
								public void run() {
									try {
										List<ModuleInfo> choices = moduleService.search(keyword);
										int top = choices.size();
										if(top > 0)
											choicesResult[0] = choices.toArray(new ModuleInfo[top]);
									}
									catch(SocketException e) {
										// A user abort will cause a "Socket closed" exception We don't
										// want to report that.
										if(!"Socket closed".equals(e.getMessage()))
											te[0] = e;
									}
									catch(Exception e) {
										te[0] = e;
									}
								}
							};
							t.start();
							int idx = 0;
							while(t.isAlive()) {
								t.join(1000);
								if(monitor.isCanceled()) {
									moduleService.abortCurrentRequest();
									throw new OperationCanceledException();
								}
								if(++idx <= 5)
									monitor.worked(1);
							}
							if(te[0] != null)
								throw new InvocationTargetException(te[0]);
						}
						catch(RuntimeException e) {
							throw e;
						}
						catch(Exception e) {
							throw new InvocationTargetException(e);
						}
						finally {
							monitor.done();
						}
					}
				});
				ModuleInfo[] choices = choicesResult[0];
				if(choices.length > 0)
					return choices;
				MessageDialog.openConfirm(
					getShell(), getLocalString("_UI_No_modules_found"),
					getLocalString("_UI_No_module_matching_keyword", keyword));
			}
			catch(InvocationTargetException e) {
				Throwable t = e.getTargetException();
				StringBuilder builder = new StringBuilder();
				builder.append(t.getClass().getName());
				builder.append("\n");
				builder.append(t.getMessage());
				builder.append("\n\n(See the log view for technical details).");
				MessageDialog.openError(getShell(), "Error while communicating with the ForgeAPI.", //
					builder.toString()); //
				log.error("Error while communicating with the ForgeAPI", t);
			}
			catch(InterruptedException e) {
			}
			return zeroModules;
		}

		protected void promptForModuleSelection() {
			InputDialog askKeywordDialog = new InputDialog(
				getShell(), getLocalString("_UI_Keyword_Search_title"), getLocalString("_UI_Keyword_Search_message"),
				null, new IInputValidator() {
					@Override
					public String isValid(String newText) {
						newText = trimToNull(newText);
						if(newText != null) {
							if(newText.length() > 30)
								return getLocalString("_UI_Keyword_Search_max_length_exceeded", 30);
							Matcher m = OK_KEYWORD_CHARACTERS.matcher(newText);
							if(!m.matches())
								return getLocalString("_UI_Keyword_Search_bad_characters");
							m = KEYWORD_AT_LEAST_ONE_CHARACTER.matcher(newText);
							if(m.find())
								return null;
						}
						return getLocalString("_UI_Keyword_Search_at_least_one_character");
					}
				});

			if(askKeywordDialog.open() != Window.OK)
				return;

			ModuleInfo[] choices = getModuleChoices(askKeywordDialog.getValue());
			if(choices.length == 0)
				return;

			ModuleListSelectionDialog dialog = new ModuleListSelectionDialog(getShell());
			dialog.setMultipleSelection(false);
			dialog.setElements(choices);

			if(module != null) {
				dialog.setInitialElementSelections(Collections.singletonList(module));
			}

			if(dialog.open() == Window.OK) {
				setModule((ModuleInfo) dialog.getFirstResult());
			}
		}

		protected void setModule(ModuleInfo selectedModule) {
			module = selectedModule;

			moduleField.setText(StringUtil.getModuleText(module));
			projectNameField.setText(module.getFullName().getName());

			validatePage();
		}

		@Override
		public void setVisible(boolean visible) {
			super.setVisible(visible);

			if(visible) {
				moduleField.setFocus();
			}
		}

		@Override
		protected boolean validatePage() {

			if(module == null) {
				setErrorMessage(null);
				setMessage(getLocalString("_UI_ModuleCannotBeEmpty_message")); //$NON-NLS-1$
				return false;
			}

			if(super.validatePage()) {
				String preferredProjectName = module.getFullName().getName();

				if(!preferredProjectName.equals(getProjectName())) {
					setErrorMessage(null);
					setMessage(
						getLocalString("_UI_ProjectNameShouldMatchModule_message", preferredProjectName), WARNING); //$NON-NLS-1$
				}

				return true;
			}

			return false;
		}

	}

	private static final Pattern OK_KEYWORD_CHARACTERS = Pattern.compile("^[0-9A-Za-z_-]*$");

	private static final Pattern KEYWORD_AT_LEAST_ONE_CHARACTER = Pattern.compile("[A-Za-z]+");

	private static final Logger log = Logger.getLogger(NewPuppetProjectFromForgeWizard.class);

	protected ModuleInfo module;

	@Override
	protected String getProjectCreationPageDescription() {
		return getLocalString("_UI_PuppetProjectFromForge_description"); //$NON-NLS-1$
	}

	@Override
	protected String getProjectCreationPageTitle() {
		return getLocalString("_UI_PuppetProjectFromForge_title"); //$NON-NLS-1$
	}

	@Override
	public void init(IWorkbench workbench, IStructuredSelection selection) {
		setDefaultPageImageDescriptor(ExtendedImageRegistry.INSTANCE.getImageDescriptor(UIPlugin.INSTANCE.getImage("full/wizban/NewPuppetProject.png"))); //$NON-NLS-1$
		setWindowTitle(getLocalString("_UI_NewPuppetProjectFromForge_title")); //$NON-NLS-1$
	}

	@Override
	protected void initializeProjectContents(IProgressMonitor monitor) throws Exception {

		if(module == null)
			return;

		SubMonitor submon = SubMonitor.convert(monitor, "Generating project...", 100);
		try {
			VersionRange vr = module.getVersion() == null
					? null
					: VersionRange.exact(module.getVersion());

			File projectDir = project.getLocation().toFile();
			getForge().install(module.getFullName(), vr, projectDir, true, true);

			File moduleFile = new File(projectDir, MODULEFILE_NAME);
			boolean moduleFileExists = moduleFile.exists();
			if(moduleFileExists) {
				// Check if this file is viable.
				boolean renameModuleFile = false;
				Diagnostic chain = new Diagnostic();
				Metadata receiver = new Metadata();
				try {
					ModuleUtils.parseModulefile(moduleFile, receiver, chain);
					for(Diagnostic problem : chain)
						if(problem.getSeverity() >= Diagnostic.ERROR) {
							// Trust the metadata.json as the source and recreate the Modulefile
							renameModuleFile = true;
							break;
						}
				}
				catch(Exception e) {
					renameModuleFile = true;
				}

				if(renameModuleFile) {
					File renamedModuleFile = new File(projectDir, MODULEFILE_NAME + ".ignored");
					renamedModuleFile.delete();
					if(moduleFile.renameTo(renamedModuleFile))
						moduleFileExists = false;
				}
			}

			submon.worked(60);

			if(submon.isCanceled())
				throw new OperationCanceledException();

			// This will cause a build. The build will recreate the metadata.json file
			project.refreshLocal(IResource.DEPTH_INFINITE, submon.newChild(30));
			if(moduleFileExists) {
				IFile mdjsonFile = ResourceUtil.getFile(project.getFullPath().append(METADATA_JSON_NAME));
				if(mdjsonFile.exists())
					mdjsonFile.setDerived(true, null);
			}
		}
		finally {
			monitor.done();
		}
	}

	@Override
	protected WizardNewProjectCreationPage newProjectCreationPage(String pageName) {
		return new PuppetProjectFromForgeCreationPage(pageName);
	}

}
