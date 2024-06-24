/*******************************************************************************
 * Copyright (c) 2022-23 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.operations.inlayhint;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.codemining.AbstractCodeMiningProvider;
import org.eclipse.jface.text.codemining.ICodeMining;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ServerCapabilities;

public class InlayHintProvider extends AbstractCodeMiningProvider {

	private CompletableFuture<List<? extends ICodeMining>> provideCodeMinings(@NonNull IDocument document) {
		URI docURI = LSPEclipseUtils.toUri(document);
		if (docURI != null) {
			// Eclipse seems to request minings only when the document is loaded (or changed), rather than
			// whenever the viewport [displayed area] changes, so request minings for the whole document in one go.
			var end = new Position(0,0);
			try {
				end = LSPEclipseUtils.toPosition(document.getLength(), document);
			} catch (BadLocationException e) {
				LanguageServerPlugin.logWarning("Unable to compute end of document", e); //$NON-NLS-1$
			}
			Range viewPortRange = new Range(new Position(0,0), end);
			InlayHintParams param = new InlayHintParams(LSPEclipseUtils.toTextDocumentIdentifier(docURI), viewPortRange);
			List<LSPLineContentCodeMining> inlayHintResults = Collections.synchronizedList(new ArrayList<>());
			return LanguageServers.forDocument(document).withCapability(ServerCapabilities::getInlayHintProvider)
					.collectAll((w, ls) -> ls.getTextDocumentService().inlayHint(param).thenAcceptAsync(inlayHints -> {
						// textDocument/inlayHint may return null
						if (inlayHints != null) {
							inlayHints.stream().filter(Objects::nonNull)
									.map(inlayHint -> toCodeMining(document, w, inlayHint))
									.filter(Objects::nonNull)
									.forEach(inlayHintResults::add);
						}
					})).thenApplyAsync(theVoid -> inlayHintResults);
		} else {
			return null;
		}
	}

	private LSPLineContentCodeMining toCodeMining(@NonNull IDocument document, @NonNull LanguageServerWrapper languageServerWrapper,
			@NonNull InlayHint inlayHint) {
		try {
			return new LSPLineContentCodeMining(inlayHint, document, languageServerWrapper, InlayHintProvider.this);
		} catch (BadLocationException e) {
			LanguageServerPlugin.logError(e);
			return null;
		}
	}

	@Override
	public CompletableFuture<List<? extends ICodeMining>> provideCodeMinings(ITextViewer viewer,
			IProgressMonitor monitor) {
		IDocument document = viewer.getDocument();
		return document != null ? provideCodeMinings(document) : null;
	}

}
