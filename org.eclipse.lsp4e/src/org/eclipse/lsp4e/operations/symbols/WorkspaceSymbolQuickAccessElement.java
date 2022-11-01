/*******************************************************************************
 * Copyright (c) 2019 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.operations.symbols;

import java.util.Random;
import java.util.function.Function;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.outline.SymbolsLabelProvider;
import org.eclipse.lsp4e.ui.UI;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.ui.quickaccess.QuickAccessElement;

public class WorkspaceSymbolQuickAccessElement extends QuickAccessElement {

	private static final SymbolsLabelProvider LABEL_PROVIDER = new SymbolsLabelProvider(false);
	private static final Random randomNumbers = new Random();

	private final WorkspaceSymbol symbol;
	private final int idExtension;

	public WorkspaceSymbolQuickAccessElement(WorkspaceSymbol symbol) {
		this.symbol = symbol;

		// this random number id extension is a workaround for
		// https://bugs.eclipse.org/bugs/show_bug.cgi?id=550835
		//
		// it avoids once selected symbols to disappear from the quick access list
		this.idExtension = randomNumbers.nextInt();
	}

	@Override
	public String getLabel() {
		return LABEL_PROVIDER.getText(symbol);
	}

	@Override
	public ImageDescriptor getImageDescriptor() {
		return ImageDescriptor.createFromImage(LABEL_PROVIDER.getImage(symbol));
	}

	@Override
	public String getId() {
		Location location = symbol.getLocation().map(Function.identity(), symbol -> new Location(symbol.getUri(), null));
		@Nullable Range range = location.getRange();
		return symbol.getName() + '@' + location.getUri() + (range != null ? '[' + range.getStart().getLine() + ',' + range.getStart().getCharacter() + ':' + range.getEnd().getLine() + ',' + range.getEnd().getCharacter() + ']' : "") + ',' + idExtension; //$NON-NLS-1$
	}

	@Override
	public void execute() {
		LSPEclipseUtils.openInEditor(symbol.getLocation().map(Function.identity(), symbol -> new Location(symbol.getUri(), null)), UI.getActivePage());
	}

}
