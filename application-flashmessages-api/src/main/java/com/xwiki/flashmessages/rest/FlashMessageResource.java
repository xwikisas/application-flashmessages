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
package com.xwiki.flashmessages.rest;

import javax.ws.rs.Encoded;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

import org.xwiki.rest.XWikiRestException;
import org.xwiki.stability.Unstable;

/**
 * Provides operations on flash message pages.
 *
 * @version $Id$
 * @since 1.11.0
 */
@Path("/wikis/{wikiName}/spaces/{spaceName: .+}/pages/{pageName}/flash/seen")
@Unstable
public interface FlashMessageResource
{
    /**
     * Mark a flash message as seen by the current user.
     *
     * @param wikiName the name of the wiki in which the flash message page resides
     * @param spaces the spaces of the flash message page
     * @param pageName the name of the flash message page
     * @param formToken the CSRF token of the current user, sent in the {@code XWiki-Form-Token} header
     * @return 200 if the message was marked as seen, 401 for guest users, 403 if the form token is invalid or the
     *     current user can't view the message, or 404 if the page is not a flash message
     * @throws XWikiRestException when failing to retrieve or save the flash message page
     */
    @PUT
    Response markAsSeen(
        @PathParam("wikiName") String wikiName,
        @PathParam("spaceName") @Encoded String spaces,
        @PathParam("pageName") String pageName,
        @HeaderParam("XWiki-Form-Token") String formToken
    ) throws XWikiRestException;
}
