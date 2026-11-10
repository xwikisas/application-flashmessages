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
package com.xwiki.flashmessages.script;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.script.service.ScriptService;
import org.xwiki.stability.Unstable;

import com.xpn.xwiki.XWikiException;
import com.xwiki.flashmessages.internal.FlashMessagesManager;

/**
 * Script service for the flash messages.
 *
 * @version $Id$
 * @since 1.11.0
 */
@Component
@Named("flashmessages")
@Singleton
@Unstable
public class FlashMessagesScriptService implements ScriptService
{
    @Inject
    private FlashMessagesManager manager;

    @Inject
    private Logger logger;

    /**
     * @return the flash messages to display to the current user today, most recent first, or an empty list if they
     *     can't be retrieved
     */
    public List<DocumentReference> getMessages()
    {
        try {
            return this.manager.getMessages();
        } catch (Exception e) {
            this.logger.warn("Failed to get the flash messages. Root cause is [{}].",
                ExceptionUtils.getRootCauseMessage(e));
            return List.of();
        }
    }

    /**
     * @param messageReference a flash message
     * @return {@code true} if the current user should confirm the message (popup)
     */
    public boolean needsConfirmation(DocumentReference messageReference)
    {
        try {
            return this.manager.needsConfirmation(messageReference);
        } catch (XWikiException e) {
            this.logger.warn("Failed to check if [{}] needs confirmation. Root cause is [{}].", messageReference,
                ExceptionUtils.getRootCauseMessage(e));
            return false;
        }
    }
}
