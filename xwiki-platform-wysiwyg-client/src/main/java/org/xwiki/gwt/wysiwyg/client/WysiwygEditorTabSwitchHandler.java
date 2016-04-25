/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.xwiki.gwt.wysiwyg.client;

import java.util.HashMap;
import java.util.Map;

import org.xwiki.gwt.user.client.ActionEvent;
import org.xwiki.gwt.user.client.CancelableAsyncCallback;
import org.xwiki.gwt.user.client.Console;
import org.xwiki.gwt.user.client.ui.rta.Reloader;
import org.xwiki.gwt.user.client.ui.rta.SelectionPreserver;
import org.xwiki.gwt.user.client.ui.rta.cmd.Command;
import org.xwiki.gwt.wysiwyg.client.converter.HTMLConverter;
import org.xwiki.gwt.wysiwyg.client.converter.HTMLConverterAsync;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.event.logical.shared.BeforeSelectionEvent;
import com.google.gwt.event.logical.shared.BeforeSelectionHandler;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.TabPanel;

/**
 * {@link WysiwygEditor} tab-switch handler.
 * 
 * @version $Id$
 */
public class WysiwygEditorTabSwitchHandler implements SelectionHandler<Integer>, BeforeSelectionHandler<Integer>
{
    /**
     * The command used to store the value of the rich text area before submitting the including form.
     */
    private static final Command SUBMIT = new Command("submit");

    /**
     * The underlying WYSIWYG editor instance.
     */
    private final WysiwygEditor editor;

    /**
     * The component used to convert the HTML generated by the WYSIWYG editor to source syntax.
     */
    private final HTMLConverterAsync converter = GWT.create(HTMLConverter.class);

    /**
     * The object used to reload the rich text area.
     */
    private final Reloader reloader;

    /**
     * The syntax used by the source editor.
     */
    private final String sourceSyntax;

    /**
     * The object notified when the response for the conversion from HTML to source is received.
     */
    private CancelableAsyncCallback<String> sourceCallback;

    /**
     * The object notified when the response for the conversion from source to HTML is received. This object is used
     * only if the {@code templateURL} is not provided, i.e. if {@link #reloader} is {@code null}.
     */
    private CancelableAsyncCallback<String> wysiwygCallback;

    /**
     * The last HTML converted to source. This helps us prevent converting the same rich text to source multiple times,
     * like when the user switches tabs without changing the content.
     */
    private String lastConvertedHTML;

    /**
     * The last source text converted to HTML. This helps us prevent converting the same source text to HTML multiple
     * times, like when the user switches tabs without changing the content.
     */
    private String lastConvertedSourceText;

    /**
     * The object used to save the DOM selection before the rich text area is hidden (i.e. before the source tab is
     * selected) and to restore it when the user switches back to WYSIWYG tab without having changed the source text.
     */
    private SelectionPreserver domSelectionPreserver;

    /**
     * Marks the end points of the source text selection before the plain text area is hidden (i.e. before the WYSIWYG
     * tab is selected). This information is used to restore the selection on the plain text area when the user switches
     * back to source tab without having changed the rich text.
     */
    private int[] sourceRange = new int[2];

    /**
     * Creates a new tab-switch handler for the given WYSIWYG editor.
     * 
     * @param editor the {@link WysiwygEditor} instance
     */
    WysiwygEditorTabSwitchHandler(WysiwygEditor editor)
    {
        this.editor = editor;
        String templateURL = editor.getConfig().getTemplateURL();
        reloader = templateURL == null ? null : new Reloader(editor.getRichTextEditor().getTextArea(), templateURL);
        sourceSyntax = editor.getConfig().getSyntax();
        domSelectionPreserver = new SelectionPreserver(editor.getRichTextEditor().getTextArea());
    }

    @Override
    public void onBeforeSelection(BeforeSelectionEvent<Integer> event)
    {
        int currentlySelectedTab = ((TabPanel) event.getSource()).getTabBar().getSelectedTab();
        if (event.getItem() == currentlySelectedTab) {
            // Tab already selected.
            event.cancel();
            return;
        }

        switch (currentlySelectedTab) {
            case WysiwygEditorConfig.WYSIWYG_TAB_INDEX:
                if (!editor.getRichTextEditor().isLoading()) {
                    // Notify the plug-ins that the content of the rich text area is about to be submitted.
                    // We have to do this before the tabs are actually switched because plug-ins can't access the
                    // computed style of the rich text area when it is hidden.
                    editor.getRichTextEditor().getTextArea().getCommandManager().execute(SUBMIT);
                    // Save the DOM selection before the rich text area is hidden.
                    domSelectionPreserver.saveSelection();
                }
                break;
            case WysiwygEditorConfig.SOURCE_TAB_INDEX:
                if (!editor.getPlainTextEditor().isLoading()) {
                    // Save the source selection before the plain text area is hidden.
                    sourceRange[0] = editor.getPlainTextEditor().getTextArea().getCursorPos();
                    sourceRange[1] = editor.getPlainTextEditor().getTextArea().getSelectionLength();
                }
                break;
            default:
                break;
        }

        String[] actionNames = new String[] {"showingWysiwyg", "showingSource"};
        ActionEvent.fire(editor.getRichTextEditor().getTextArea(), actionNames[event.getItem()]);
    }

    @Override
    public void onSelection(SelectionEvent<Integer> event)
    {
        if (event.getSelectedItem() == WysiwygEditorConfig.WYSIWYG_TAB_INDEX) {
            switchToWysiwyg();
        } else {
            switchToSource();
        }
    }

    /**
     * Disables the rich text editor, enables the source editor and updates the source text.
     */
    private void switchToSource()
    {
        // If the rich text editor is loading then there's no HTML to convert to source.
        if (editor.getRichTextEditor().isLoading()) {
            // The plain text area lost the focus while it was hidden. We have to restore its selection.
            restoreSourceSelection();
        } else {
            // At this point we should have the HTML, adjusted by plug-ins, submitted.
            // See #onBeforeSelection(BeforeSelectionEvent)
            String currentHTML = editor.getRichTextEditor().getTextArea().getCommandManager().getStringValue(SUBMIT);
            // If the HTML didn't change then there's no point in doing the conversion again.
            if (!currentHTML.equals(lastConvertedHTML)) {
                convertFromHTML(currentHTML);
            } else if (!editor.getPlainTextEditor().isLoading()) {
                enableSourceTab();
            }
        }
    }

    /**
     * Converts the given HTML fragment to source and updates the plain text area.
     * 
     * @param html the HTML fragment to be converted to source
     */
    public void convertFromHTML(String html)
    {
        // Update the HTML to prevent duplicated requests while the conversion is in progress.
        lastConvertedHTML = html;
        // Clear the saved source selection range because a new source text will be loaded. Place the caret at
        // start.
        sourceRange[0] = 0;
        sourceRange[1] = 0;
        // If there is a conversion is progress, cancel it.
        if (sourceCallback != null) {
            sourceCallback.setCanceled(true);
        } else {
            editor.getPlainTextEditor().setLoading(true);
        }
        sourceCallback = new CancelableAsyncCallback<String>(new AsyncCallback<String>()
        {
            @Override
            public void onFailure(Throwable caught)
            {
                sourceCallback = null;
                onSwitchToSourceFailure(caught);
            }

            @Override
            public void onSuccess(String result)
            {
                sourceCallback = null;
                onSwitchToSourceSuccess(result);
            }
        });
        // Make the request to convert the HTML to source syntax.
        converter.fromHTML(html, sourceSyntax, sourceCallback);
    }

    /**
     * The conversion from HTML to source failed.
     * 
     * @param caught the cause of the failure
     */
    private void onSwitchToSourceFailure(Throwable caught)
    {
        Console.getInstance().error(caught.getLocalizedMessage());
        // Reset the last converted HTML to retry the conversion.
        lastConvertedHTML = null;
        // Move back to the WYSIWYG tab to prevent losing data.
        editor.setSelectedTab(WysiwygEditorConfig.WYSIWYG_TAB_INDEX);
    }

    /**
     * The conversion from HTML to source succeeded.
     * 
     * @param source the result of the conversion
     */
    private void onSwitchToSourceSuccess(String source)
    {
        // Update the source to prevent a useless source to HTML conversion when we already have the HTML.
        lastConvertedSourceText = source;
        // Update the plain text editor.
        editor.getPlainTextEditor().getTextArea().setText(source);
        editor.getPlainTextEditor().setLoading(false);
        // If we are still on the source tab..
        if (editor.getSelectedTab() == WysiwygEditorConfig.SOURCE_TAB_INDEX) {
            enableSourceTab();
        }
    }

    /**
     * Disables the rich text editor and enables the source editor.
     */
    private void enableSourceTab()
    {
        // Disable the rich text area to avoid submitting its content.
        editor.getRichTextEditor().getTextArea().getCommandManager().execute(Command.ENABLE, false);

        // Enable the source editor in order to be able to submit its content.
        if (editor.getConfig().isEnabled()) {
            editor.getPlainTextEditor().getTextArea().setEnabled(true);
        }
        // Store the initial value of the plain text area in case it is submitted without gaining focus.
        editor.getPlainTextEditor().submit();
        // Remember the fact that the submitted value is not HTML for the case when the editor is loaded from cache.
        editor.getConfig().setInputConverted(false);
        // Restore the selected text or just place the caret at start.
        restoreSourceSelection();
    }

    /**
     * Restores the previously selected source text, or just places the caret at start.
     */
    private void restoreSourceSelection()
    {
        // Try giving focus to the plain text area (this might not work if the browser window is not focused).
        editor.getPlainTextEditor().getTextArea().setFocus(true);
        // Restore the selected text or place the caret at start.
        editor.getPlainTextEditor().getTextArea().setSelectionRange(sourceRange[0], sourceRange[1]);
        // Notify action listeners that the source tab was loaded. We fire the action event here because this method is
        // called both when the source text area is reloaded and when it is just redisplayed.
        ActionEvent.fire(editor.getRichTextEditor().getTextArea(), "showSource");
    }

    /**
     * Disables the source editor, enables the rich text editor and updates the rich text.
     */
    private void switchToWysiwyg()
    {
        // If the plain text editor is loading then there's no source text to convert to HTML.
        if (editor.getPlainTextEditor().isLoading()) {
            // The rich text area lost the focus while it was hidden. We have to restore its selection.
            // NOTE: We have to use a deferred command in order to let the rich text area re-initialize its internal
            // selection object after it was hidden. The internal selection object is null at this point.
            Scheduler.get().scheduleDeferred(new com.google.gwt.user.client.Command()
            {
                @Override
                public void execute()
                {
                    restoreDOMSelection();
                }
            });
        } else {
            String currentSourceText = editor.getPlainTextEditor().getTextArea().getText();
            // If the source text didn't change then there's no point in doing the conversion again.
            if (!currentSourceText.equals(lastConvertedSourceText)) {
                convertToHTML(currentSourceText);
            } else if (!editor.getRichTextEditor().isLoading()) {
                // NOTE: We have to use a deferred command in order to let the rich text area re-initialize its internal
                // selection object after it was hidden. The internal selection object is null at this point.
                Scheduler.get().scheduleDeferred(new com.google.gwt.user.client.Command()
                {
                    @Override
                    public void execute()
                    {
                        // Double check the selected tab.
                        if (editor.getSelectedTab() == WysiwygEditorConfig.WYSIWYG_TAB_INDEX
                            && !editor.getRichTextEditor().isLoading()) {
                            enableWysiwygTab();
                        }
                    }
                });
            }
        }
    }

    /**
     * Converts the given source text to HTML and updates the rich text area.
     * 
     * @param source the source text to be converted to HTML
     */
    public void convertToHTML(String source)
    {
        // Update the source text to prevent duplicated conversion requests while the conversion is in progress.
        lastConvertedSourceText = source;
        // Clear the saved selection because the document is reloaded.
        domSelectionPreserver.clearSelection();
        editor.getRichTextEditor().setLoading(true);
        if (reloader != null) {
            convertToHTMLWithTemplate(source);
        } else {
            convertToHTMLWithoutTemplate(source);
        }
    }

    /**
     * Converts the given source text to HTML using the provided rich text area template and updates the rich text area.
     * 
     * @param source the source text to be converted to HTML
     */
    private void convertToHTMLWithTemplate(String source)
    {
        // Reload the rich text area.
        Map<String, String> params = new HashMap<String, String>();
        params.put("source", source);
        reloader.reload(params, new AsyncCallback<Void>()
        {
            @Override
            public void onFailure(Throwable caught)
            {
                onSwitchToWysiwygFailure(caught);
            }

            @Override
            public void onSuccess(Void result)
            {
                onSwitchToWysiwygSuccess();
            }
        });
    }

    /**
     * Converts the given source text to HTML using the HTML converter.
     * 
     * @param source the source text to be converted to HTML
     */
    private void convertToHTMLWithoutTemplate(String source)
    {
        // If there is a conversion is progress, cancel it.
        if (wysiwygCallback != null) {
            wysiwygCallback.setCanceled(true);
        }
        wysiwygCallback = new CancelableAsyncCallback<String>(new AsyncCallback<String>()
        {
            @Override
            public void onFailure(Throwable caught)
            {
                wysiwygCallback = null;
                onSwitchToWysiwygFailure(caught);
            }

            @Override
            public void onSuccess(String result)
            {
                wysiwygCallback = null;
                editor.getRichTextEditor().getTextArea().setHTML(result);
                onSwitchToWysiwygSuccess();
            }
        });
        // Make the request to convert the source text to HTML.
        converter.toHTML(source, sourceSyntax, wysiwygCallback);
    }

    /**
     * The conversion from source text to HTML failed.
     * 
     * @param caught the cause of the failure
     */
    private void onSwitchToWysiwygFailure(Throwable caught)
    {
        Console.getInstance().error(caught.getLocalizedMessage());
        // Reset the last converted source text to retry the conversion.
        lastConvertedSourceText = null;
        // Move back to the source tab to prevent losing data.
        editor.setSelectedTab(WysiwygEditorConfig.SOURCE_TAB_INDEX);
    }

    /**
     * The conversion from source text to HTML succeeded.
     */
    private void onSwitchToWysiwygSuccess()
    {
        // Reset the content of the rich text area.
        editor.getRichTextEditor().getTextArea().getCommandManager().execute(Command.RESET);
        // If we are still on the WYSIWYG tab..
        if (editor.getSelectedTab() == WysiwygEditorConfig.WYSIWYG_TAB_INDEX) {
            enableWysiwygTab();
        }
        editor.getRichTextEditor().setLoading(false);
    }

    /**
     * Disables the source editor and enables the rich text editor.
     */
    private void enableWysiwygTab()
    {
        // Disable the plain text area (if present) to prevent submitting its content.
        PlainTextEditor plainTextEditor = editor.getPlainTextEditor();
        if (plainTextEditor != null) {
            plainTextEditor.getTextArea().setEnabled(false);
        }

        // Enable the rich text area in order to be able to edit and submit its content.
        // We have to enable the rich text area before initializing the rich text editor because some of the editing
        // features are loaded only when the rich text area is enabled.
        if (editor.getConfig().isEnabled()) {
            editor.getRichTextEditor().getTextArea().getCommandManager().execute(Command.ENABLE, true);
        }
        // Initialize the rich text editor if this is the first time we switch to WYSIWYG tab.
        editor.maybeInitializeRichTextEditor();
        // Restore the DOM selection before executing the commands.
        restoreDOMSelection();
        // Store the initial value of the rich text area in case it is submitted without gaining focus.
        editor.getRichTextEditor().getTextArea().getCommandManager().execute(SUBMIT, true);
        // Update the HTML to prevent a useless HTML to source conversion when we already know the source.
        lastConvertedHTML = editor.getRichTextEditor().getTextArea().getCommandManager().getStringValue(SUBMIT);
        // Remember the fact that the submitted value is HTML for the case when the editor is loaded from cache.
        editor.getConfig().setInputConverted(true);
    }

    /**
     * Restores the selection on the rich text area. It does nothing if the selection wan't previously saved.
     */
    private void restoreDOMSelection()
    {
        if (domSelectionPreserver.hasSelection()) {
            // Focus the rich text area only if there is a previously saved selection (otherwise we steal the focus).
            editor.getRichTextEditor().getTextArea().setFocus(true);
        }
        // Restore the DOM selection. Puts the caret at the beginning of the document if there is no saved selection
        // (otherwise the caret would be at the end, see XWIKI-6672).
        domSelectionPreserver.restoreSelection();
        // Notify action listeners that the WYSIWYG tab was loaded. We fire the action event here because this method is
        // called both when the rich text area is reloaded and when it is just redisplayed.
        ActionEvent.fire(editor.getRichTextEditor().getTextArea(), "showWysiwyg");
    }
}
