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
package org.xwiki.gwt.wysiwyg.client.plugin.link.ui;

import org.xwiki.gwt.user.client.StringUtils;
import org.xwiki.gwt.wysiwyg.client.Strings;
import org.xwiki.gwt.wysiwyg.client.plugin.link.LinkConfig.LinkType;
import org.xwiki.gwt.wysiwyg.client.plugin.link.ui.LinkWizard.LinkWizardStep;
import org.xwiki.gwt.wysiwyg.client.wiki.WikiPageReference;
import org.xwiki.gwt.wysiwyg.client.wiki.WikiServiceAsync;

import com.google.gwt.user.client.rpc.AsyncCallback;

/**
 * Specialized {@link AbstractExplorerWizardStep} to select a wiki page (existing or new).
 * 
 * @version $Id$
 */
public class WikiPageExplorerWizardStep extends AbstractExplorerWizardStep
{
    /**
     * Creates a wiki page selection wizard step that allows the user to select the page to link to from a tree.
     * 
     * @param wikiService the service used to parse and serialize page references
     */
    public WikiPageExplorerWizardStep(WikiServiceAsync wikiService)
    {
        // Build a standard selector which shows "Add page" and no attachments.
        // FIXME: Size hard-coding is very bad, remove when a method to control this from CSS will be found.
        super(wikiService, true, false, false, 455, 280);

        setStepTitle(Strings.INSTANCE.linkSelectWikipageTitle());
        setHelpLabelText(Strings.INSTANCE.linkSelectWikipageHelpLabel());
    }

    /**
     * {@inheritDoc}
     */
    public String getNextStep()
    {
        WikiPageReference wikiPageReference = new WikiPageReference(getData().getDestination().getEntityReference());
        return StringUtils.isEmpty(wikiPageReference.getPageName()) ? LinkWizardStep.WIKI_PAGE_CREATOR.toString()
            : LinkWizardStep.WIKI_PAGE_CONFIG.toString();
    }

    /**
     * {@inheritDoc}
     */
    public void onSubmit(final AsyncCallback<Boolean> async)
    {
        hideError();

        WikiPageReference pageReference = new WikiPageReference();
        pageReference.setWikiName(getExplorer().getSelectedWiki());
        pageReference.setSpaceName(getExplorer().getSelectedSpace());
        pageReference.setPageName(getExplorer().getSelectedPage());

        if (getExplorer().isNewPageSelectedFromTreeNode()) {
            getData().getData().setType(LinkType.NEW_WIKIPAGE);
            getData().getDestination().setEntityReference(pageReference.getEntityReference());
            async.onSuccess(true);
        } else if (StringUtils.isEmpty(pageReference.getPageName())) {
            displayError(Strings.INSTANCE.linkNoPageSelectedError());
            async.onSuccess(false);
        } else if (!StringUtils.isEmpty(getData().getData().getReference())
            && getData().getDestination().getEntityReference().equals(pageReference.getEntityReference())) {
            // The link destination has not changed and is already serialized.
            async.onSuccess(true);
        } else {
            updateLinkConfig(pageReference.getEntityReference(), new AsyncCallback<Boolean>()
            {
                public void onFailure(Throwable caught)
                {
                    async.onFailure(caught);
                }

                public void onSuccess(Boolean result)
                {
                    if (result) {
                        LinkType linkType = getExplorer().isNewPage() ? LinkType.NEW_WIKIPAGE : LinkType.WIKIPAGE;
                        getData().getData().setType(linkType);
                    }
                    async.onSuccess(result);
                }
            });
        }
    }
}
