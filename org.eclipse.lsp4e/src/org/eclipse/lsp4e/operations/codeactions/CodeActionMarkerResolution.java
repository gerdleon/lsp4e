/*******************************************************************************
 * Copyright (c) 2018, 2022 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *  Max Bureck (Fraunhofer FOKUS) - integeration with CommandExecutor
 *******************************************************************************/
package org.eclipse.lsp4e.operations.codeactions;

import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.LanguageServersRegistry;
import org.eclipse.lsp4e.LanguageServersRegistry.LanguageServerDefinition;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.ServerMessageHandler;
import org.eclipse.lsp4e.command.CommandExecutor;
import org.eclipse.lsp4e.operations.diagnostics.LSPDiagnosticsToMarkers;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.ExecuteCommandOptions;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.IMarkerResolution;
import org.eclipse.ui.views.markers.WorkbenchMarkerResolution;

public class CodeActionMarkerResolution extends WorkbenchMarkerResolution implements IMarkerResolution {

	private CodeAction codeAction;

	public CodeActionMarkerResolution(CodeAction codeAction) {
		this.codeAction = codeAction;
	}

	@Override
	public String getDescription() {
		return codeAction.getTitle();
	}

	@Override
	public Image getImage() {
		return null;
	}

	@Override
	public String getLabel() {
		return codeAction.getTitle();
	}

	@Override
	public void run(IMarker marker) {
		if (codeAction.getEdit() != null) {
			LSPEclipseUtils.applyWorkspaceEdit(codeAction.getEdit(), codeAction.getTitle());
			return;
		}
		String languageServerId = marker.getAttribute(LSPDiagnosticsToMarkers.LANGUAGE_SERVER_ID, null);
		LanguageServerDefinition definition = languageServerId != null ? LanguageServersRegistry.getInstance().getDefinition(languageServerId) : null;
		try {
			LanguageServerWrapper wrapper = null;
			if (definition != null) {
				IResource resource = marker.getResource();
				if (resource != null) {
					wrapper = LanguageServiceAccessor.getLSWrapper(resource.getProject(), definition);
				}
			}
			if (wrapper != null) {
				if (codeAction.getEdit() == null) {
					if (CodeActionCompletionProposal.isCodeActionResolveSupported(wrapper.getServerCapabilities())) {
						CodeAction resolvedCodeAction = wrapper.execute(ls -> ls.getTextDocumentService().resolveCodeAction(codeAction)).get(2, TimeUnit.SECONDS);
						if (resolvedCodeAction != null) {
							codeAction = resolvedCodeAction;
						}
					}
				}
				if (codeAction.getEdit() != null) {
					LSPEclipseUtils.applyWorkspaceEdit(codeAction.getEdit(), codeAction.getTitle());
				}
				if (codeAction.getCommand() != null) {
					Command command = codeAction.getCommand();
					ExecuteCommandOptions provider = wrapper.getServerCapabilities().getExecuteCommandProvider();
					if (provider != null && provider.getCommands().contains(command.getCommand())) {
						final LanguageServerDefinition serverDefinition = wrapper.serverDefinition;
						wrapper.execute(ls -> ls.getWorkspaceService()
								.executeCommand(new ExecuteCommandParams(command.getCommand(), command.getArguments()))
								.exceptionally(t -> reportServerError(serverDefinition, t))
						);
					} else  {
						IResource resource = marker.getResource();
						if (resource != null) {
							CommandExecutor.executeCommandClientSide(command, resource);
						}
					}
				}
			}
		} catch (ExecutionException | TimeoutException | InterruptedException ex) {
			LanguageServerPlugin.logError(ex);
		}
	}

	private ShowMessageRequestParams reportServerError(LanguageServerDefinition serverDefinition, Throwable t) {
		ShowMessageRequestParams params = new ShowMessageRequestParams();
		String title = "Error Executing Quick Fix"; //$NON-NLS-1$
		params.setType(MessageType.Error);
		params.setMessage("Failed to fetch quick fix edit for '" //$NON-NLS-1$
				+ codeAction.getTitle()
				+ "'. See Language Server '" //$NON-NLS-1$
				+ serverDefinition.id
				+ "' log for more details."); //$NON-NLS-1$
		ServerMessageHandler.showMessage(title, params);
		return params;
	}

	@Override
	public IMarker[] findOtherMarkers(IMarker[] markers) {
		if (markers == null) {
			return new IMarker[0];
		}
		return Arrays.stream(markers).filter(marker -> {
			try {
				return codeAction.getDiagnostics()
						.contains(marker.getAttribute(LSPDiagnosticsToMarkers.LSP_DIAGNOSTIC));
			} catch (CoreException e) {
				LanguageServerPlugin.logError(e);
				return false;
			}
		}).toArray(IMarker[]::new);
	}

}
