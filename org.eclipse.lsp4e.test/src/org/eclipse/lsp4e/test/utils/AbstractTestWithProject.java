/*******************************************************************************
 * Copyright (c) 2024 Vegard IT GmbH and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Sebastian Thomschke (Vegard IT GmbH) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.utils;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.junit.After;
import org.junit.Before;

/**
 * Test base class that provides a new unique temporary test project for each @org.junit.Test run
 */
public abstract class AbstractTestWithProject extends AbstractTest {
	protected IProject project;

	@Before
	public void setUpProject() throws Exception {
		project = TestUtils.createProject(
				testInfo.getSimpleClassName() + "_" + testInfo.getMethodName() + "_" + System.currentTimeMillis());
	}

	@After
	public void tearDownProject() throws Exception {
		project.delete(IResource.FORCE, null);
	}
}
