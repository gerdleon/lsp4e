/*******************************************************************************
 * Copyright (c) 2023 Contributors to the Eclipse Foundation.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   See git history
 *******************************************************************************/
package org.eclipse.lsp4e.format;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.osgi.service.component.annotations.Component;

/**
 * Default OSGi service implementation if no bundle provides a OSGi service for {@link IFormatRegionsProvider}.
 *
 */
@Component
public class DefaultFormatRegionsProvider implements IFormatRegionsProvider {

	@Override
	public IRegion[] getFormattingRegions(IDocument document) {
		// return null until user can disable the format-on-save feature in LSP4E.
		return null;
	}

}
