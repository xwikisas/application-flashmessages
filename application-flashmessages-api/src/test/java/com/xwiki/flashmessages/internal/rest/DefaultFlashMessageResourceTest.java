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

import java.util.List;

import javax.inject.Provider;
import javax.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.xwiki.component.util.DefaultParameterizedType;
import org.xwiki.csrf.CSRFToken;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.test.mockito.MockitoComponentManager;

import com.xpn.xwiki.XWikiContext;
import com.xwiki.flashmessages.internal.FlashMessagesManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultFlashMessageResource}.
 *
 * @version $Id$
 */
@ComponentTest
class DefaultFlashMessageResourceTest
{
    private static final DocumentReference MESSAGE_REFERENCE =
        new DocumentReference("wiki", List.of("Flash", "Messages"), "Message");

    private static final String FORM_TOKEN = "token";

    @InjectMockComponents
    private DefaultFlashMessageResource resource;

    @MockComponent
    private ContextualAuthorizationManager authorizationManager;

    @MockComponent
    private FlashMessagesManager manager;

    @MockComponent
    private CSRFToken csrfToken;

    @Mock
    private XWikiContext xcontext;

    @BeforeEach
    void setUp(MockitoComponentManager componentManager) throws Exception
    {
        Provider<XWikiContext> xcontextProvider =
            componentManager.getInstance(new DefaultParameterizedType(null, Provider.class, XWikiContext.class));
        when(xcontextProvider.get()).thenReturn(this.xcontext);
        when(this.xcontext.getUserReference()).thenReturn(new DocumentReference("wiki", "XWiki", "Alice"));
        when(this.authorizationManager.hasAccess(Right.VIEW, MESSAGE_REFERENCE)).thenReturn(true);
        when(this.csrfToken.isTokenValid(FORM_TOKEN)).thenReturn(true);
    }

    @Test
    void markAsSeen() throws Exception
    {
        when(this.manager.markAsSeen(MESSAGE_REFERENCE)).thenReturn(true);

        assertEquals(200, callMarkAsSeen().getStatus());
    }

    @Test
    void markAsSeenWhenNotAFlashMessage() throws Exception
    {
        assertEquals(404, callMarkAsSeen().getStatus());
    }

    @Test
    void markAsSeenWhenGuest() throws Exception
    {
        when(this.xcontext.getUserReference()).thenReturn(null);

        assertEquals(401, callMarkAsSeen().getStatus());
        verify(this.manager, never()).markAsSeen(any());
    }

    @Test
    void markAsSeenWithoutViewRight() throws Exception
    {
        when(this.authorizationManager.hasAccess(Right.VIEW, MESSAGE_REFERENCE)).thenReturn(false);

        assertEquals(403, callMarkAsSeen().getStatus());
        verify(this.manager, never()).markAsSeen(any());
    }

    @Test
    void markAsSeenWithInvalidFormToken() throws Exception
    {
        assertEquals(403, this.resource.markAsSeen("wiki", "Flash/spaces/Messages", "Message", null).getStatus());
        assertEquals(403, this.resource.markAsSeen("wiki", "Flash/spaces/Messages", "Message", "bad").getStatus());
        verify(this.manager, never()).markAsSeen(any());
    }

    private Response callMarkAsSeen() throws Exception
    {
        return this.resource.markAsSeen("wiki", "Flash/spaces/Messages", "Message", FORM_TOKEN);
    }
}
