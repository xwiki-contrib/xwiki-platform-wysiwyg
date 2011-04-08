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
package org.xwiki.gwt.wysiwyg.client.cleaner;

import org.xwiki.component.annotation.ComponentRole;

import com.google.gwt.user.client.rpc.RemoteService;
import com.google.gwt.user.client.rpc.RemoteServiceRelativePath;

/**
 * Cleans the HTML generated by the WYSIWYG editor. Its main purpose is to remove or transform the HTML elements that
 * were added by the editor only for internal reasons.
 * 
 * @version $Id$
 */
@ComponentRole
@RemoteServiceRelativePath("HTMLCleaner.gwtrpc")
public interface HTMLCleaner extends RemoteService
{
    /**
     * @param dirtyHTML the string containing the HTML output of the WYSIWYG editor
     * @return the cleaned HTML
     */
    String clean(String dirtyHTML);
}
