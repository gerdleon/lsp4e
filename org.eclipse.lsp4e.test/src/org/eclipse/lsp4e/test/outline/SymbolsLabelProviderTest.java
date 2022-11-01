/*******************************************************************************
 * Copyright (c) 2017, 2019 TypeFox and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Jan Koehnlein (TypeFox) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.outline;

import static org.junit.Assert.assertEquals;

import org.eclipse.lsp4e.outline.SymbolsLabelProvider;
import org.eclipse.lsp4e.test.AllCleanRule;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.junit.Rule;
import org.junit.Test;

public class SymbolsLabelProviderTest {

	@Rule public AllCleanRule clear = new AllCleanRule();
	private static final Location LOCATION = new Location("path/to/foo", new Range(new Position(0,0), new Position(1,1)));
	private static final Location INVALID_LOCATION = new Location("file:://///invalid_location_uri", new Range(new Position(0,0), new Position(1,1)));

	@Test
	public void testShowLocation() {
		SymbolsLabelProvider labelProvider = new SymbolsLabelProvider(true);
		SymbolInformation info = new SymbolInformation("Foo", SymbolKind.Class, LOCATION);
		assertEquals("Foo path/to/foo", labelProvider.getText(info));
	}

	@Test
	public void testShowNeither() {
		SymbolsLabelProvider labelProvider = new SymbolsLabelProvider(false);
		SymbolInformation info = new SymbolInformation("Foo", SymbolKind.Class, LOCATION);
		assertEquals("Foo", labelProvider.getText(info));
	}

	@Test
	public void testGetStyledTextInalidLocationURI() {
		SymbolsLabelProvider labelProvider = new SymbolsLabelProvider(false);
		SymbolInformation info = new SymbolInformation("Foo", SymbolKind.Class, INVALID_LOCATION);
		assertEquals("Foo", labelProvider.getStyledText(info).getString());
	}
}
