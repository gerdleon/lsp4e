/*******************************************************************************
 * Copyright (c) 2016, 2017 Rogue Wave Software Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *  Max Bureck (Fraunhofer FOKUS) - added test for executing commands on completions
 *******************************************************************************/
package org.eclipse.lsp4e.test.completion;

import static org.eclipse.lsp4e.test.TestUtils.waitForAndAssertCondition;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.LanguageServersRegistry;
import org.eclipse.lsp4e.LanguageServersRegistry.LanguageServerDefinition;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.operations.completion.LSCompletionProposal;
import org.eclipse.lsp4e.test.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4e.ui.UI;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.InsertTextMode;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ST;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.ui.PartInitException;
import org.junit.Test;

import com.google.gson.JsonPrimitive;

public class CompleteCompletionTest extends AbstractCompletionTest {
	/*
	 * This tests the not-so-official way to associate a LS to a file programmatically, and then to retrieve the LS
	 * for the file independently of the content-types. Although doing it programmatically isn't recommended, consuming
	 * file-specific LS already associated is something we want to support.
	 */
	@Test
	public void testAssistForUnknownButConnectedType() throws CoreException, IOException {
		List<CompletionItem> items = new ArrayList<>();
		items.add(createCompletionItem("FirstClass", CompletionItemKind.Class));
		MockLanguageServer.INSTANCE.setCompletionList(new CompletionList(false, items));

		IFile testFile = TestUtils.createUniqueTestFileOfUnknownType(project, "");
		ITextViewer viewer = TestUtils.openTextViewer(testFile);

		LanguageServerDefinition serverDefinition = LanguageServersRegistry.getInstance().getDefinition("org.eclipse.lsp4e.test.server");
		assertNotNull(serverDefinition);
		LanguageServerWrapper lsWrapper = LanguageServiceAccessor.getLSWrapper(testFile.getProject(), serverDefinition);
		URI fileLocation = testFile.getLocationURI();
		// force connection (that's what LSP4E should be designed to prevent 3rd party from having to use it).
		lsWrapper.connect(null, testFile);

		waitForAndAssertCondition(3_000, () -> lsWrapper.isConnectedTo(fileLocation));

		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, 0);
		assertEquals(items.size(), proposals.length);
		// TODO compare both structures
		LSCompletionProposal lsCompletionProposal = (LSCompletionProposal)proposals[0];
		lsCompletionProposal.apply(viewer, '\n', 0, 0);
		assertEquals(new Point("FirstClass".length(), 0), lsCompletionProposal.getSelection(viewer.getDocument()));
	}

	@Test
	public void testNoPrefix() throws CoreException {
		List<CompletionItem> items = new ArrayList<>();
		items.add(createCompletionItem("FirstClass", CompletionItemKind.Class));
		MockLanguageServer.INSTANCE.setCompletionList(new CompletionList(false, items));

		IFile testFile = TestUtils.createUniqueTestFile(project, "");
		ITextViewer viewer = TestUtils.openTextViewer(testFile);

		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, 0);
		assertEquals(items.size(), proposals.length);
		// TODO compare both structures
		LSCompletionProposal lsCompletionProposal = (LSCompletionProposal)proposals[0];
		lsCompletionProposal.apply(viewer, '\n', 0, 0);
		assertEquals(new Point("FirstClass".length(), 0), lsCompletionProposal.getSelection(viewer.getDocument()));
	}

	@Test
	public void testPrefix() throws CoreException {
		List<CompletionItem> items = new ArrayList<>();
		items.add(createCompletionItem("FirstClass", CompletionItemKind.Class));
		items.add(createCompletionItem("SecondClass", CompletionItemKind.Class));
		MockLanguageServer.INSTANCE.setCompletionList(new CompletionList(false, items));

		String content = "First";
		ITextViewer viewer = TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project, content));

		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, content.length());
		assertEquals(1, proposals.length);
		// TODO compare items
		LSCompletionProposal lsCompletionProposal = (LSCompletionProposal)proposals[0];
		lsCompletionProposal.apply(viewer, '\n', 0, 0);
		assertEquals(new Point("FirstClass".length(), 0), lsCompletionProposal.getSelection(viewer.getDocument()));
	}

	/*
	 * This test checks if a Command associated with a completion that is applied will be executed.
	 * The test will use a Command that shall be handled by the langauge server.
	 */
	@Test
	public void testCommandExecution() throws CoreException, InterruptedException, ExecutionException, TimeoutException {
		CompletionItem completionItem = createCompletionItem("Bla", CompletionItemKind.Class);
		String expectedParameter = "command execution parameter";
		List<Object> commandArguments = Arrays.asList(expectedParameter);
		completionItem.setCommand(new Command("TestCommand", MockLanguageServer.SUPPORTED_COMMAND_ID, commandArguments));

		MockLanguageServer.INSTANCE.setCompletionList(new CompletionList(false, Arrays.asList(completionItem)));

		ITextViewer viewer = TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project, ""));

		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, 0);
		assertEquals(1, proposals.length);

		LSCompletionProposal lsCompletionProposal = (LSCompletionProposal)proposals[0];
		lsCompletionProposal.apply(viewer, '\n', 0, 0);

		// Assert command was invoked on langauge server
		ExecuteCommandParams executedCommand = MockLanguageServer.INSTANCE.getWorkspaceService().getExecutedCommand().get(2, TimeUnit.SECONDS);

		assertEquals(MockLanguageServer.SUPPORTED_COMMAND_ID, executedCommand.getCommand());
		List<JsonPrimitive> expectedParameterList = Arrays.asList(new JsonPrimitive(expectedParameter));
		assertEquals(expectedParameterList, executedCommand.getArguments());
	}

	@Test
	public void testPrefixCaseSensitivity() throws CoreException {
		List<CompletionItem> items = new ArrayList<>();
		items.add(createCompletionItem("FirstClass", CompletionItemKind.Class));
		MockLanguageServer.INSTANCE.setCompletionList(new CompletionList(false, items));

		String content = "FIRST";
		ITextViewer viewer = TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project, content));

		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, content.length());
		assertEquals(1, proposals.length);
		// TODO compare items
		LSCompletionProposal lsCompletionProposal = (LSCompletionProposal)proposals[0];
		lsCompletionProposal.apply(viewer, '\n', 0, 0);
		assertEquals(new Point("FirstClass".length(), 0), lsCompletionProposal.getSelection(viewer.getDocument()));
	}

	@Test
	public void testCompleteOnFileEnd() throws CoreException { // bug 508842
		CompletionItem item = new CompletionItem();
		item.setLabel("1024M");
		item.setKind(CompletionItemKind.Value);
		item.setTextEdit(Either.forLeft(new TextEdit(new Range(new Position(2, 10), new Position(2, 10)), "1024M")));
		CompletionList completionList = new CompletionList(false, Collections.singletonList(item));
		MockLanguageServer.INSTANCE.setCompletionList(completionList);

		String content = "applications:\n" + "- name: hello\n" + "  memory: ";
		ITextViewer viewer = TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project, content));

		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, content.length());
		assertEquals(1, proposals.length);

		LSCompletionProposal lsCompletionProposal = (LSCompletionProposal)proposals[0];
		lsCompletionProposal.apply(viewer, '\n', 0, content.length());
		assertEquals(content + "1024M", viewer.getDocument().get());
		assertEquals(new Point(viewer.getDocument().getLength(), 0), lsCompletionProposal.getSelection(viewer.getDocument()));
	}

	@Test
	public void testTriggerCharsWithoutPreliminaryCompletion() throws CoreException { // bug 508463
		Set<String> triggers = new HashSet<>();
		triggers.add("a");
		triggers.add("b");
		MockLanguageServer.INSTANCE.setCompletionTriggerChars(triggers);

		String content = "First";
		TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project, content));

		waitForAndAssertCondition(3_000, () -> Arrays.equals(
			new char[] { 'a', 'b'},
			contentAssistProcessor.getCompletionProposalAutoActivationCharacters()
		));
	}

	@Test
	public void testTriggerCharsNullList() throws CoreException {
		MockLanguageServer.INSTANCE.setCompletionTriggerChars(null);

		TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project, "First"));

		assertArrayEquals(new char[0], contentAssistProcessor.getCompletionProposalAutoActivationCharacters());
	}

	@Test
	public void testApplyCompletionWithPrefix() throws CoreException {
		Range range = new Range(new Position(0, 0), new Position(0, 5));
		List<CompletionItem> items = Collections
				.singletonList(createCompletionItem("FirstClass", CompletionItemKind.Class, range));
		MockLanguageServer.INSTANCE.setCompletionList(new CompletionList(false, items));

		String content = "First";
		ITextViewer viewer = TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project, content));

		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, content.length());
		LSCompletionProposal lsCompletionProposal = (LSCompletionProposal)proposals[0];
		lsCompletionProposal.apply(viewer, '\n', 0, content.length());
		assertEquals(true, viewer.getDocument().get().equals("FirstClass"));
		assertEquals(new Point(viewer.getDocument().getLength(), 0), lsCompletionProposal.getSelection(viewer.getDocument()));
	}

	@Test
	public void testApplyCompletionReplace() throws CoreException {
		Range range = new Range(new Position(0, 0), new Position(0, 20));
		List<CompletionItem> items = Collections
				.singletonList(createCompletionItem("FirstClass", CompletionItemKind.Class, range));
		MockLanguageServer.INSTANCE.setCompletionList(new CompletionList(false, items));

		String content = "FirstNotMatchedLabel";
		ITextViewer viewer = TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project, content));
		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, 5);
		LSCompletionProposal lsCompletionProposal = (LSCompletionProposal)proposals[0];
		lsCompletionProposal.apply(viewer, '\n', 0, 5);
		assertEquals("FirstClass", viewer.getDocument().get());
		assertEquals(new Point("FirstClass".length(), 0), lsCompletionProposal.getSelection(viewer.getDocument()));
	}

	@Test
	public void testApplyCompletionReplaceAndTypingWithTextEdit() throws CoreException, BadLocationException {
		Range range = new Range(new Position(0, 0), new Position(0, 20));
		List<CompletionItem> items = Collections
				.singletonList(createCompletionItem("FirstClass", CompletionItemKind.Class, range));
		MockLanguageServer.INSTANCE.setCompletionList(new CompletionList(false, items));

		String content = "FirstNotMatchedLabel";
		ITextViewer viewer = TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project,content));

		int invokeOffset = 5;
		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, invokeOffset);
		LSCompletionProposal lsCompletionProposal = (LSCompletionProposal)proposals[0];

		// simulate additional typing (to filter) after invoking completion
		viewer.getDocument().replace(5, 0, "No");

		lsCompletionProposal.apply(viewer, '\n', 0, invokeOffset + "No".length());
		assertEquals("FirstClass", viewer.getDocument().get());
		assertEquals(new Point("FirstClass".length(), 0), lsCompletionProposal.getSelection(viewer.getDocument()));
	}

	@Test
	public void testApplyCompletionReplaceAndTyping() throws CoreException, BadLocationException {
		CompletionItem item = new CompletionItem("strncasecmp");
		item.setKind(CompletionItemKind.Function);
		item.setInsertText("strncasecmp()");

		MockLanguageServer.INSTANCE.setCompletionList(new CompletionList(false,  Collections.singletonList(item)));

		String content = "str";
		ITextViewer viewer = TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project, content));

		int invokeOffset = content.length();
		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, invokeOffset);
		assertEquals(1, proposals.length);
		LSCompletionProposal lsCompletionProposal = (LSCompletionProposal) proposals[0];

		// simulate additional typing (to filter) after invoking completion
		viewer.getDocument().replace(content.length(), 0, "nc");

		lsCompletionProposal.apply(viewer, '\0', 0, invokeOffset + "nc".length());
		assertEquals(item.getInsertText(), viewer.getDocument().get());
		assertEquals(new Point(item.getInsertText().length(), 0), lsCompletionProposal.getSelection(viewer.getDocument()));
	}

	@Test
	public void testCompletionReplace() throws CoreException {
		IFile file = TestUtils.createUniqueTestFile(project, "line1\nlineInsertHere");
		ITextViewer viewer = TestUtils.openTextViewer(file);
		MockLanguageServer.INSTANCE.setCompletionList(new CompletionList(false, Collections.singletonList(
			createCompletionItem("Inserted", CompletionItemKind.Text, new Range(new Position(1, 4), new Position(1, 4 + "InsertHere".length())))
		)));

		int invokeOffset = viewer.getDocument().getLength() - "InsertHere".length();
		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, invokeOffset);
		LSCompletionProposal lsCompletionProposal = (LSCompletionProposal)proposals[0];
		lsCompletionProposal.apply(viewer, '\n', 0, invokeOffset);
		assertEquals("line1\nlineInserted", viewer.getDocument().get());
		assertEquals(new Point(viewer.getDocument().getLength(), 0), lsCompletionProposal.getSelection(viewer.getDocument()));
	}

	@Test
	public void testItemOrdering() throws Exception {
		Range range = new Range(new Position(0, 0), new Position(0, 1));
		List<CompletionItem> items = Arrays.asList(new CompletionItem[] {
			createCompletionItem("AA", CompletionItemKind.Class, range),
			createCompletionItem("AB", CompletionItemKind.Class, range),
			createCompletionItem("BA", CompletionItemKind.Class, range),
			createCompletionItem("BB", CompletionItemKind.Class, range),
			createCompletionItem("CB", CompletionItemKind.Class, range),
			createCompletionItem("CC", CompletionItemKind.Class, range),
		});
		MockLanguageServer.INSTANCE.setCompletionList(new CompletionList(false, items));

		String content = "B";
		ITextViewer viewer = TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project,content));

		int invokeOffset = 1;
		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, invokeOffset);
		assertEquals(4, proposals.length); // only those containing a "B"
		assertEquals("BA", proposals[0].getDisplayString());
		assertEquals("BB", proposals[1].getDisplayString());
		assertEquals("AB", proposals[2].getDisplayString());
		assertEquals("CB", proposals[3].getDisplayString());

		((LSCompletionProposal) proposals[0]).apply(viewer, '\n', 0, invokeOffset);
		assertEquals("BA", viewer.getDocument().get());
	}

	@Test
	public void testBasicSnippet() throws PartInitException, CoreException {
		CompletionItem completionItem = createCompletionItem("$1 and ${2:foo}", CompletionItemKind.Class, new Range(new Position(0, 0), new Position(0, 1)));
		completionItem.setInsertTextFormat(InsertTextFormat.Snippet);
		MockLanguageServer.INSTANCE.setCompletionList(new CompletionList(false, Collections.singletonList(completionItem)));
		ITextViewer viewer = TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project,""));
		int invokeOffset = 0;
		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, invokeOffset);
		assertEquals(1, proposals.length);
		((LSCompletionProposal) proposals[0]).apply(viewer, '\n', 0, invokeOffset);
		assertEquals(" and foo", viewer.getDocument().get());
		// TODO check link edit groups
	}

	@Test
	public void testChoiceSnippet() throws PartInitException, CoreException {
		CompletionItem completionItem = createCompletionItem("1${1|a,b|}2", CompletionItemKind.Class);
		completionItem.setInsertTextFormat(InsertTextFormat.Snippet);
		MockLanguageServer.INSTANCE.setCompletionList(new CompletionList(false, Collections.singletonList(completionItem)));
		ITextViewer viewer = TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project,""));
		int invokeOffset = 0;
		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, invokeOffset);
		assertEquals(1, proposals.length);
		Set<Shell> beforeShells = new HashSet<>(Arrays.asList(viewer.getTextWidget().getDisplay().getShells()));
		((LSCompletionProposal) proposals[0]).apply(viewer, '\n', 0, invokeOffset);
		assertEquals("1a2", viewer.getDocument().get());
		Set<Shell> newShells = new HashSet<>(Arrays.asList(viewer.getTextWidget().getDisplay().getShells()));
		newShells.removeAll(beforeShells);
		assertNotEquals(Collections.emptySet(), newShells);
		Table proposalList = (Table)newShells.iterator().next().getChildren()[0];
		String[] itemLabels = Arrays.stream(proposalList.getItems()).map(TableItem::getText).toArray(String[]::new);
		assertArrayEquals(new String[] {"a", "b"}, itemLabels);
	}

	@Test
	public void testDuplicateVariable() throws PartInitException, CoreException {
		CompletionItem completionItem = createCompletionItem("${1:foo} and ${1:foo}", CompletionItemKind.Class, new Range(new Position(0, 0), new Position(0, 1)));
		completionItem.setInsertTextFormat(InsertTextFormat.Snippet);
		MockLanguageServer.INSTANCE.setCompletionList(new CompletionList(false, Collections.singletonList(completionItem)));
		ITextViewer viewer = TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project,""));
		int invokeOffset = 0;
		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, invokeOffset);
		assertEquals(1, proposals.length);
		((LSCompletionProposal) proposals[0]).apply(viewer, '\n', 0, invokeOffset);
		assertEquals("foo and foo", viewer.getDocument().get());
		// TODO check link edit groups
	}

	@Test
	public void testSnippetTabStops() throws PartInitException, CoreException {
		CompletionItem completionItem = createCompletionItem("sum(${1:x}, ${2:y})", CompletionItemKind.Method,
				new Range(new Position(0, 0), new Position(0, 1)));
		completionItem.setInsertTextFormat(InsertTextFormat.Snippet);
		MockLanguageServer.INSTANCE
				.setCompletionList(new CompletionList(false, Collections.singletonList(completionItem)));
		ITextViewer viewer = TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project, ""));
		int invokeOffset = 0;
		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, invokeOffset);
		assertEquals(1, proposals.length);
		((LSCompletionProposal) proposals[0]).apply(viewer, '\n', 0, invokeOffset);
		assertEquals("sum(x, y)", viewer.getDocument().get());
		// after the proposal is applied, the x parameter should be selected
		Point range = proposals[0].getSelection(viewer.getDocument());
		assertEquals(4, range.x);
		assertEquals(1, range.y);

		// fake a VerifyKey tabbing event to jump to the y parameter
		Event event = new Event();
		event.character = SWT.TAB;
		viewer.getTextWidget().notifyListeners(ST.VerifyKey, event);
		range = viewer.getSelectedRange();
		assertEquals(7, range.x);
		assertEquals(1, range.y);
	}

	@Test
	public void testMultipleLS() throws Exception {
		List<CompletionItem> items = new ArrayList<>();
		items.add(createCompletionItem("FirstClass", CompletionItemKind.Class));
		MockLanguageServer.INSTANCE.setCompletionList(new CompletionList(false, items));

		IFile testFile = TestUtils.createUniqueTestFileMultiLS(project, "");
		ITextViewer viewer = TestUtils.openTextViewer(testFile);

		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, 0);
		assertEquals(2 * (long)(items.size()), proposals.length);
	}

	@Test
	public void testReopeningFileAndReusingContentAssist() throws CoreException {
		List<CompletionItem> items = new ArrayList<>();
		items.add(createCompletionItem("FirstClass", CompletionItemKind.Class));
		MockLanguageServer.INSTANCE.setCompletionList(new CompletionList(false, items));

		IFile testFile = TestUtils.createUniqueTestFile(project, "");
		ITextViewer viewer = TestUtils.openTextViewer(testFile);

		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, 0);
		assertEquals(items.size(), proposals.length); // TODO compare both structures
		LSCompletionProposal lsCompletionProposal = (LSCompletionProposal) proposals[0];
		lsCompletionProposal.apply(viewer, '\n', 0, 0);
		assertEquals(new Point("FirstClass".length(), 0), lsCompletionProposal.getSelection(viewer.getDocument()));

		UI.getActivePage().closeAllEditors(false);
		MockLanguageServer.reset();

		MockLanguageServer.INSTANCE.setCompletionList(new CompletionList(false, items));
		viewer = TestUtils.openTextViewer(testFile);

		proposals = contentAssistProcessor.computeCompletionProposals(viewer, 0);
		assertEquals(items.size(), proposals.length); // TODO compare both structures
		lsCompletionProposal = (LSCompletionProposal) proposals[0];
		lsCompletionProposal.apply(viewer, '\n', 0, 0);
		assertEquals(new Point("FirstClass".length(), 0), lsCompletionProposal.getSelection(viewer.getDocument()));
	}

	@Test
	public void testFilterNonmatchingCompletions() throws Exception {
		List<CompletionItem> items = new ArrayList<>();
		CompletionItem item = new CompletionItem("server.web");
		item.setFilterText("server.web");
		item.setTextEdit(Either.forLeft(new TextEdit(new Range(new Position(0, 0), new Position(0, 10)), item.getFilterText())));
		items.add(item);
		// 'soup' replacing the 'ver' in 'server' does not make sense when knowing that
		// ver should have been a filter
		item = new CompletionItem("soup");
		item.setFilterText("soup");
		item.setTextEdit(Either.forLeft(new TextEdit(new Range(new Position(0, 3), new Position(0, 7)), item.getFilterText())));
		items.add(item);
		items.add(new CompletionItem(": 1.0.1"));
		items.add(new CompletionItem("s.Status"));

		confirmCompletionResults(items, "server", 6, new String[] { "server.web", ": 1.0.1", "s.Status" });
	}

	@Test
	public void testFilterNonmatchingCompletionsMovieOffset() throws Exception {
		IFile testFile = TestUtils.createUniqueTestFile(project, "servers");
		IDocument document = TestUtils.openTextViewer(testFile).getDocument();
		LanguageServerWrapper wrapper = LanguageServiceAccessor.getLSWrappers(testFile, capabilities -> capabilities.getCompletionProvider() != null
				|| capabilities.getSignatureHelpProvider() != null)
				.get(0);
		// The completion ': 1.0.1' was given, then the user types a 's', which is used
		// as a filter and removes the completion
		LSCompletionProposal completionProposal = new LSCompletionProposal(document, 0, new CompletionItem(": 1.0.1"),
				wrapper);
		assertTrue(completionProposal.isValidFor(document, 6));
		assertFalse(completionProposal.isValidFor(document, 7));
	}

	@Test
	public void testAdjustIndentation() throws Exception {
		ITextViewer viewer = TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project, "a\n\tb\n\t\nc"));
		CompletionItem item = new CompletionItem("line1\nline2");
		item.setInsertTextMode(InsertTextMode.AdjustIndentation);
		MockLanguageServer.INSTANCE.setCompletionList(new CompletionList(false, List.of(item)));
		int invokeOffset = 6;
		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, invokeOffset);
		assertEquals(1, proposals.length);
		((LSCompletionProposal) proposals[0]).apply(viewer, '\n', 0, invokeOffset);
		assertEquals("a\n\tb\n\tline1\n\tline2\nc", viewer.getDocument().get());
	}
}
