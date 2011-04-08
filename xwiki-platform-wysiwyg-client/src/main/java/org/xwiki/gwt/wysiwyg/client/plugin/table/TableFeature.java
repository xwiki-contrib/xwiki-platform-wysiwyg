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
package org.xwiki.gwt.wysiwyg.client.plugin.table;

import org.xwiki.gwt.user.client.ui.rta.cmd.Command;
import org.xwiki.gwt.user.client.ui.rta.cmd.Executable;

/**
 * TableFeature API. 
 * 
 * @version $Id$
 */
public interface TableFeature extends Executable
{
    /**
     * Get feature name.
     * 
     * @return feature name (examples: inserttable, insertrowbefore).
     */
    String getName();

    /**
     * Get feature command.
     * 
     * @return feature command.
     */
    Command getCommand();
    
    /**
     * Get table plugin.
     * 
     * @return table plugin.
     */
    TablePlugin getPlugin();
    
    /**
     * Remove feature button from the interface.
     */
    void destroy();
}
