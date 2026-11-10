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
package com.xwiki.flashmessages.internal.rest;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.core.Response;

import org.xwiki.component.annotation.Component;
import org.xwiki.csrf.CSRFToken;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.rest.XWikiResource;
import org.xwiki.rest.XWikiRestException;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;

import com.xpn.xwiki.XWikiException;
import com.xwiki.flashmessages.internal.FlashMessagesManager;
import com.xwiki.flashmessages.rest.FlashMessageResource;

/**
 * Default implementation of {@link FlashMessageResource}.
 *
 * @version $Id$
 * @since 1.11.0
 */
@Component
@Named("com.xwiki.flashmessages.internal.rest.DefaultFlashMessageResource")
@Singleton
public class DefaultFlashMessageResource extends XWikiResource implements FlashMessageResource
{
    @Inject
    private ContextualAuthorizationManager authorizationManager;

    @Inject
    private FlashMessagesManager manager;

    @Inject
    private CSRFToken csrfToken;

    @Override
    public Response markAsSeen(String wikiName, String spaces, String pageName, String formToken)
        throws XWikiRestException
    {
        if (getXWikiContext().getUserReference() == null) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        // The REST filter checks the form token only for POST requests with form content types.
        DocumentReference documentReference = new DocumentReference(pageName, getSpaceReference(spaces, wikiName));
        if (!this.csrfToken.isTokenValid(formToken)
            || !this.authorizationManager.hasAccess(Right.VIEW, documentReference))
        {
            return Response.status(Response.Status.FORBIDDEN).build();
        }

        try {
            return this.manager.markAsSeen(documentReference) ? Response.ok().build()
                : Response.status(Response.Status.NOT_FOUND).build();
        } catch (XWikiException e) {
            throw new XWikiRestException(e);
        }
    }
}
