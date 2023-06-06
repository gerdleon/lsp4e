/*******************************************************************************
 * Copyright (c) 2017, 2019 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.codeactions;

import static org.eclipse.lsp4e.test.TestUtils.waitForAndAssertCondition;
import static org.eclipse.lsp4e.test.TestUtils.waitForCondition;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourceAttributes;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.operations.diagnostics.LSPDiagnosticsToMarkers;
import org.eclipse.lsp4e.test.AllCleanRule;
import org.eclipse.lsp4e.test.NoErrorLoggedRule;
import org.eclipse.lsp4e.test.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.texteditor.AbstractTextEditor;
import org.eclipse.ui.texteditor.ITextEditorActionConstants;
import org.eclipse.ui.texteditor.TextOperationAction;
import org.junit.Rule;
import org.junit.Test;

public class CodeActionTests {

	@Rule public NoErrorLoggedRule rule = new NoErrorLoggedRule(LanguageServerPlugin.getDefault().getLog());
	@Rule public AllCleanRule clear = new AllCleanRule();

	@Test
	public void testCodeActionsClientCommandForTextEdit() throws CoreException {
		IProject p = TestUtils.createProject(getClass().getSimpleName() + System.currentTimeMillis());
		IFile f = TestUtils.createUniqueTestFile(p, "error");
		MockLanguageServer.INSTANCE.setCodeActions(Collections.singletonList(Either.forLeft(new Command(
				"fixme",
				"edit",
				Collections.singletonList(
					new TextEdit(
							new Range(new Position(0, 0), new Position(0, 5)),
							"fixed"))
				)
			)
		));
		MockLanguageServer.INSTANCE.setDiagnostics(Collections.singletonList(
				new Diagnostic(new Range(new Position(0, 0), new Position(0, 5)), "error", DiagnosticSeverity.Error, null)));
		AbstractTextEditor editor = (AbstractTextEditor)TestUtils.openEditor(f);
		try {
			IMarker m = assertDiagnostics(f, "error", "fixme");
			assertResolution(editor, m, "fixed");
		} finally {
			editor.close(false);
			p.delete(true, new NullProgressMonitor());
		}
	}

	@Test
	public void testCodeActionsClientCommandForWorkspaceEdit() throws CoreException {
		IProject p = TestUtils.createProject(getClass().getSimpleName() + System.currentTimeMillis());
		IFile f = TestUtils.createUniqueTestFile(p, "error");

		TextEdit tEdit = new TextEdit(new Range(new Position(0, 0), new Position(0, 5)), "fixed");
		WorkspaceEdit wEdit = new WorkspaceEdit(Collections.singletonMap(f.getLocationURI().toString(), Collections.singletonList(tEdit)));
		MockLanguageServer.INSTANCE.setCodeActions(Collections
				.singletonList(Either.forLeft(new Command(
				"fixme",
				"edit",
				Collections.singletonList(wEdit))
			)
		));
		MockLanguageServer.INSTANCE.setDiagnostics(Collections.singletonList(
				new Diagnostic(new Range(new Position(0, 0), new Position(0, 5)), "error", DiagnosticSeverity.Error, null)));
		AbstractTextEditor editor = (AbstractTextEditor)TestUtils.openEditor(f);

		IMarker m = assertDiagnostics(f, "error", "fixme");
		assertResolution(editor, m, "fixed");
	}

	private void checkCompletionContent(final Table completionProposalList) {
		// should be instantaneous, but happens to go asynchronous on CI so let's allow a wait
		waitForAndAssertCondition("No item found", 100, () -> completionProposalList.getItemCount() > 0);
		assertEquals(1, completionProposalList.getItemCount());
		final TableItem quickAssistItem = completionProposalList.getItem(0);
		assertTrue("Missing quick assist proposal", quickAssistItem.getText().contains("fixme")); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	public void testCodeActionsQuickAssist() throws CoreException {
		MockLanguageServer.reset();
		IProject p = TestUtils.createProject(getClass().getSimpleName() + System.currentTimeMillis());
		IFile f = TestUtils.createUniqueTestFile(p, "error");

		TextEdit tEdit = new TextEdit(new Range(new Position(0, 0), new Position(0, 5)), "fixed");
		WorkspaceEdit wEdit = new WorkspaceEdit(Collections.singletonMap(f.getLocationURI().toString(), Collections.singletonList(tEdit)));
		MockLanguageServer.INSTANCE.setCodeActions(Collections
				.singletonList(Either.forLeft(new Command(
				"fixme",
				"edit",
				Collections.singletonList(wEdit))
			)
		));
		AbstractTextEditor editor = (AbstractTextEditor)TestUtils.openEditor(f);
		final Set<Shell> beforeShells = Arrays.stream(editor.getSite().getShell().getDisplay().getShells()).filter(Shell::isVisible).collect(Collectors.toSet());
		editor.selectAndReveal(3, 0);
		TextOperationAction action = (TextOperationAction) editor.getAction(ITextEditorActionConstants.QUICK_ASSIST);
		action.update();
		action.run();
		Shell completionShell= TestUtils.findNewShell(beforeShells, editor.getSite().getShell().getDisplay());
		final Table completionProposalList = TestUtils.findCompletionSelectionControl(completionShell);
		checkCompletionContent(completionProposalList);
	}

	@Test
	public void testCodeActionLiteralWorkspaceEdit() throws CoreException {
		IProject p = TestUtils.createProject(getClass().getSimpleName() + System.currentTimeMillis());
		IFile f = TestUtils.createUniqueTestFile(p, "error");

		TextEdit tEdit = new TextEdit(new Range(new Position(0, 0), new Position(0, 5)), "fixed");
		WorkspaceEdit wEdit = new WorkspaceEdit(Collections.singletonMap(f.getLocationURI().toString(), Collections.singletonList(tEdit)));
		CodeAction codeAction = new CodeAction("fixme");
		codeAction.setEdit(wEdit);
		MockLanguageServer.INSTANCE.setCodeActions(Collections.singletonList(Either.forRight(codeAction)));
		MockLanguageServer.INSTANCE.setDiagnostics(Collections.singletonList(
				new Diagnostic(new Range(new Position(0, 0), new Position(0, 5)), "error", DiagnosticSeverity.Error, null)));
		AbstractTextEditor editor = (AbstractTextEditor)TestUtils.openEditor(f);
		IMarker m = assertDiagnostics(f, "error", "fixme");
		assertResolution(editor, m, "fixed");
	}

	@Test
	public void testNoCodeActionOnReadOnlySource() throws CoreException {
		IProject p = TestUtils.createProject(getClass().getSimpleName() + System.currentTimeMillis());
		IFile f = TestUtils.createUniqueTestFile(p, "error");
		f.setResourceAttributes(new ResourceAttributes() {
			@Override
			public boolean isReadOnly() {
				return true;
			}
		});

		TextEdit tEdit = new TextEdit(new Range(new Position(0, 0), new Position(0, 5)), "fixed");
		WorkspaceEdit wEdit = new WorkspaceEdit(Collections.singletonMap(f.getLocationURI().toString(), Collections.singletonList(tEdit)));
		CodeAction codeAction = new CodeAction("fixme");
		codeAction.setEdit(wEdit);
		MockLanguageServer.INSTANCE.setCodeActions(Collections.singletonList(Either.forRight(codeAction)));
		MockLanguageServer.INSTANCE.setDiagnostics(Collections.singletonList(
				new Diagnostic(new Range(new Position(0, 0), new Position(0, 5)), "error", DiagnosticSeverity.Error, null)));
		TestUtils.openEditor(f);
		assertDiagnostics(f, "error", "fixme", false);
	}

	@Test
	public void testCodeActionLiteralWithClientCommand() throws CoreException {
		IProject p = TestUtils.createProject(getClass().getSimpleName() + System.currentTimeMillis());
		IFile f = TestUtils.createUniqueTestFile(p, "error");

		TextEdit tEdit = new TextEdit(new Range(new Position(0, 0), new Position(0, 5)), "fixed");
		WorkspaceEdit wEdit = new WorkspaceEdit(Collections.singletonMap(f.getLocationURI().toString(), Collections.singletonList(tEdit)));
		CodeAction codeAction = new CodeAction("fixme");
		codeAction.setCommand(new Command("editCommand", "mockEditCommand", Collections.singletonList(wEdit)));
		MockLanguageServer.INSTANCE.setCodeActions(Collections.singletonList(Either.forRight(codeAction)));
		MockLanguageServer.INSTANCE.setDiagnostics(Collections.singletonList(
				new Diagnostic(new Range(new Position(0, 0), new Position(0, 5)), "error", DiagnosticSeverity.Error, null)));
		AbstractTextEditor editor = (AbstractTextEditor)TestUtils.openEditor(f);
		IMarker m = assertDiagnostics(f, "error", "fixme");
		assertResolution(editor, m, "fixed");
	}

	@Test
	public void testCodeActionWorkspaceEditlWithDifferentURI() throws CoreException {
		IProject p = TestUtils.createProject(getClass().getSimpleName() + System.currentTimeMillis());
		IFile sourceFile = TestUtils.createUniqueTestFile(p, "error");
		IFile targetFile = TestUtils.createUniqueTestFile(p, "fixme");

		// create a diagnostic on the sourceFile with a code action
		// that changes the targetFile
		TextEdit tEdit = new TextEdit(new Range(new Position(0, 0), new Position(0, 5)), "fixed");
		WorkspaceEdit wEdit = new WorkspaceEdit(Collections.singletonMap(targetFile.getLocationURI().toString(), Collections.singletonList(tEdit)));
		CodeAction codeAction = new CodeAction("fixme");
		codeAction.setCommand(new Command("editCommand", "mockEditCommand", Collections.singletonList(wEdit)));
		MockLanguageServer.INSTANCE.setCodeActions(Collections.singletonList(Either.forRight(codeAction)));
		MockLanguageServer.INSTANCE.setDiagnostics(Collections.singletonList(
				new Diagnostic(new Range(new Position(0, 0), new Position(0, 5)), "error", DiagnosticSeverity.Error, null)));

		TestUtils.openEditor(sourceFile);
		IMarker m = assertDiagnostics(sourceFile, "error", "fixme");

		// Apply and check the resolution
		assertResolution(targetFile, m, "fixed");

		// Double check that the editor is opened and active for the targetFile
		// as result of the resolution
		IEditorPart activeEditorPart = TestUtils.getActiveEditor();
		assertTrue(activeEditorPart instanceof AbstractTextEditor);
		assertEquals(new FileEditorInput(targetFile), ((AbstractTextEditor)activeEditorPart).getEditorInput());
	}

	public static IMarker assertDiagnostics(IFile f, String markerMessage, String resolutionLabel) throws CoreException {
		return assertDiagnostics(f, markerMessage, resolutionLabel, true);
	}

	public static IMarker assertDiagnostics(IFile f, String markerMessage, String resolutionLabel, boolean resolutionExpected) throws CoreException {
		waitForAndAssertCondition(2_000, () -> {
			IMarker[] markers = f.findMarkers(LSPDiagnosticsToMarkers.LS_DIAGNOSTIC_MARKER_TYPE, true,
					IResource.DEPTH_ZERO);
			// seems we need the second condition as the attributes aren't loaded immediately
			return markers.length > 0 && markers[0].getAttribute(IMarker.MESSAGE) != null;
		});
		final IMarker m = f.findMarkers(LSPDiagnosticsToMarkers.LS_DIAGNOSTIC_MARKER_TYPE, true, IResource.DEPTH_ZERO)[0];
		assertEquals(markerMessage, m.getAttribute(IMarker.MESSAGE));

		assertEquals(resolutionExpected ? "Resolution not found within expected time." : "Unexpected resolution found", resolutionExpected,
				waitForCondition(2_000, () ->
				IDE.getMarkerHelpRegistry().hasResolutions(m) &&
				// need this 2nd condition because async introduces a dummy resolution that's
				// not the one we want
				IDE.getMarkerHelpRegistry().getResolutions(m).length > 0 &&
				IDE.getMarkerHelpRegistry().getResolutions(m)[0].getLabel().equals(resolutionLabel)));
		return m;
	}

	public static void assertResolution(AbstractTextEditor editor, IMarker m, String newText) {
		IDE.getMarkerHelpRegistry().getResolutions(m)[0].run(m);

		waitForCondition(1_000,
				() -> newText.equals(editor.getDocumentProvider().getDocument(editor.getEditorInput()).get()));
		assertEquals(newText, editor.getDocumentProvider().getDocument(editor.getEditorInput()).get());

		waitForCondition(1_000,
				() -> newText.equals(((StyledText) editor.getAdapter(Control.class)).getText()));
		assertEquals(newText, ((StyledText) editor.getAdapter(Control.class)).getText());
	}

	public static void assertResolution(IFile targetFile, IMarker m, String newText) {
		IDE.getMarkerHelpRegistry().getResolutions(m)[0].run(m);

		IEditorPart editorPart = null;
		try {
			editorPart = TestUtils.getEditor(targetFile);
		} catch (PartInitException e) {
			fail(e.getMessage());
		}
		assertTrue(editorPart instanceof AbstractTextEditor);
		AbstractTextEditor editor = (AbstractTextEditor)editorPart;

		waitForCondition(1_000,
				() -> newText.equals(editor.getDocumentProvider().getDocument(editor.getEditorInput()).get()));
		assertEquals(newText, editor.getDocumentProvider().getDocument(editor.getEditorInput()).get());

		waitForCondition(1_000,
				() -> newText.equals(((StyledText) editor.getAdapter(Control.class)).getText()));
		assertEquals(newText, ((StyledText) editor.getAdapter(Control.class)).getText());
	}

}
