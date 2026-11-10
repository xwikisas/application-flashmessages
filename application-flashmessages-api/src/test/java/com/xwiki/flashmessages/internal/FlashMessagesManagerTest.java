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
package com.xwiki.flashmessages.internal;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.inject.Named;
import javax.inject.Provider;

import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.xwiki.component.util.DefaultParameterizedType;
import org.xwiki.model.document.DocumentAuthors;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.query.Query;
import org.xwiki.query.QueryManager;
import org.xwiki.security.authorization.AuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.test.mockito.MockitoComponentManager;
import org.xwiki.user.group.GroupManager;
import org.xwiki.user.group.WikiTarget;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FlashMessagesManager}.
 *
 * @version $Id$
 */
@ComponentTest
class FlashMessagesManagerTest
{
    private static final DocumentReference MESSAGE_REFERENCE =
        new DocumentReference("wiki", List.of("Flash", "Messages"), "Message");

    private static final DocumentReference USER_REFERENCE = new DocumentReference("wiki", "XWiki", "Alice");

    private static final LocalDocumentReference FLASH_CLASS = new LocalDocumentReference("Flash", "FlashClass");

    private static final LocalDocumentReference VALIDATION_CLASS =
        new LocalDocumentReference("Flash", "FlashValidationClass");

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);

    @InjectMockComponents
    private FlashMessagesManager manager;

    @MockComponent
    private GroupManager groupManager;

    @MockComponent
    private QueryManager queryManager;

    @MockComponent
    private AuthorizationManager authorization;

    @MockComponent
    private DocumentReferenceResolver<SolrDocument> solrDocumentResolver;

    @MockComponent
    private EntityReferenceSerializer<String> serializer;

    @MockComponent
    @Named("local")
    private EntityReferenceSerializer<String> localSerializer;

    @MockComponent
    @Named("compactwiki")
    private EntityReferenceSerializer<String> compactWikiSerializer;

    @Mock
    private XWikiContext xcontext;

    @Mock
    private XWiki xwiki;

    @Mock
    private XWikiDocument document;

    @Mock
    private BaseObject flashObject;

    @Mock
    private BaseObject validationObject;

    @BeforeEach
    void setUp(MockitoComponentManager componentManager) throws Exception
    {
        Provider<XWikiContext> xcontextProvider =
            componentManager.getInstance(new DefaultParameterizedType(null, Provider.class, XWikiContext.class));
        when(xcontextProvider.get()).thenReturn(this.xcontext);
        when(this.xcontext.getWiki()).thenReturn(this.xwiki);
        when(this.xcontext.getUserReference()).thenReturn(USER_REFERENCE);
        when(this.xwiki.getDocument(MESSAGE_REFERENCE, this.xcontext)).thenReturn(this.document);
        when(this.xwiki.getDocument(USER_REFERENCE, this.xcontext)).thenReturn(mock(XWikiDocument.class));
        when(this.document.getXObject(FLASH_CLASS)).thenReturn(this.flashObject);
        when(this.document.getXObject(VALIDATION_CLASS)).thenReturn(this.validationObject);
        when(this.document.clone()).thenReturn(this.document);
        when(this.document.getXObject(VALIDATION_CLASS, true, this.xcontext)).thenReturn(this.validationObject);
        when(this.document.getAuthors()).thenReturn(mock(DocumentAuthors.class));
        when(this.compactWikiSerializer.serialize(USER_REFERENCE, MESSAGE_REFERENCE)).thenReturn("XWiki.Alice");
        when(this.serializer.serialize(MESSAGE_REFERENCE)).thenReturn("wiki:Flash.Messages.Message");
    }

    @Test
    void getMessages() throws Exception
    {
        DocumentReference group = new DocumentReference("wiki", "XWiki", "Group");
        when(this.groupManager.getGroups(USER_REFERENCE, WikiTarget.ENTITY, true)).thenReturn(List.of(group));
        when(this.localSerializer.serialize(group)).thenReturn("XWiki.Group");
        when(this.serializer.serialize(group)).thenReturn("wiki:XWiki.Group");
        when(this.xcontext.getWikiId()).thenReturn("wiki");
        Query query = mock(Query.class);
        when(this.queryManager.createQuery(anyString(), eq("solr"))).thenReturn(query);
        QueryResponse response = mock(QueryResponse.class);
        when(query.execute()).thenReturn(List.of(response));

        DocumentReference olderReference = new DocumentReference("wiki", "Flash", "Older");
        XWikiDocument olderDocument = mock(XWikiDocument.class);
        BaseObject olderObject = mock(BaseObject.class);
        when(this.xwiki.getDocument(olderReference, this.xcontext)).thenReturn(olderDocument);
        when(olderDocument.getXObject(FLASH_CLASS)).thenReturn(olderObject);
        when(olderObject.getDateValue("dateBegin")).thenReturn(new Date(1000));
        when(olderObject.getDocumentReference()).thenReturn(olderReference);
        when(this.flashObject.getDateValue("dateBegin")).thenReturn(new Date(2000));
        when(this.flashObject.getDocumentReference()).thenReturn(MESSAGE_REFERENCE);
        // Deleted since Solr indexed it.
        DocumentReference deletedReference = new DocumentReference("wiki", "Flash", "Deleted");
        when(this.xwiki.getDocument(deletedReference, this.xcontext)).thenReturn(mock(XWikiDocument.class));
        for (DocumentReference viewable : List.of(olderReference, MESSAGE_REFERENCE, deletedReference)) {
            when(this.authorization.hasAccess(Right.VIEW, USER_REFERENCE, viewable)).thenReturn(true);
        }
        // Not viewable by the current user.
        DocumentReference forbiddenReference = new DocumentReference("wiki", "Flash", "Forbidden");

        SolrDocumentList results = new SolrDocumentList();
        // Translations are indexed separately but the objects are on the default one.
        results.add(solrDocument(new DocumentReference(olderReference, Locale.FRENCH)));
        results.add(solrDocument(MESSAGE_REFERENCE));
        // Another translation of the same message.
        results.add(solrDocument(new DocumentReference(MESSAGE_REFERENCE, Locale.FRENCH)));
        results.add(solrDocument(deletedReference));
        results.add(solrDocument(forbiddenReference));
        when(response.getResults()).thenReturn(results);

        assertEquals(List.of(MESSAGE_REFERENCE, olderReference), this.manager.getMessages());
        verify(this.xwiki, never()).getDocument(forbiddenReference, this.xcontext);
        verify(this.xwiki).getDocument(MESSAGE_REFERENCE, this.xcontext);
        // Only the clause built from the user's groups, the rest of the filter is fixed.
        verify(query).bindValue(eq("fq"),
            contains("(wiki:wiki AND property.Flash.FlashClass.groups_string:XWiki.Group) OR "
                + "(-wiki:wiki AND property.Flash.FlashClass.groups_string:wiki\\:XWiki.Group)"));
    }

    @Test
    void getMessagesWhenGuest() throws Exception
    {
        when(this.xcontext.getUserReference()).thenReturn(null);

        assertEquals(List.of(), this.manager.getMessages());
        verifyNoInteractions(this.groupManager, this.queryManager);
    }

    @Test
    void shouldDisplay()
    {
        assertTrue(this.manager.shouldDisplay(this.flashObject, TODAY));

        when(this.flashObject.getIntValue("repeat")).thenReturn(1);
        when(this.flashObject.getStringValue("repeatFrequency")).thenReturn("2");
        when(this.flashObject.getDateValue("dateBegin")).thenReturn(toDate(LocalDate.of(2026, 10, 20)));
        when(this.flashObject.getStringValue("repeatInterval")).thenReturn("daily");
        assertTrue(this.manager.shouldDisplay(this.flashObject, TODAY));
        assertFalse(this.manager.shouldDisplay(this.flashObject, TODAY.plusDays(1)));

        // 2026-10-28 is a Wednesday, two weeks after the start.
        when(this.flashObject.getDateValue("dateBegin")).thenReturn(toDate(LocalDate.of(2026, 10, 14)));
        when(this.flashObject.getStringValue("repeatInterval")).thenReturn("weekly");
        when(this.flashObject.getListValue("repeatDays")).thenReturn(List.of("wednesday"));
        assertTrue(this.manager.shouldDisplay(this.flashObject, TODAY));
        assertFalse(this.manager.shouldDisplay(this.flashObject, TODAY.plusWeeks(1)));

        when(this.flashObject.getDateValue("dateBegin")).thenReturn(toDate(LocalDate.of(2026, 8, 28)));
        when(this.flashObject.getStringValue("repeatInterval")).thenReturn("monthly");
        assertTrue(this.manager.shouldDisplay(this.flashObject, TODAY));
        assertFalse(this.manager.shouldDisplay(this.flashObject, TODAY.plusDays(1)));

        when(this.flashObject.getDateValue("dateBegin")).thenReturn(toDate(LocalDate.of(2024, 10, 28)));
        when(this.flashObject.getStringValue("repeatInterval")).thenReturn("yearly");
        assertTrue(this.manager.shouldDisplay(this.flashObject, TODAY));
        assertFalse(this.manager.shouldDisplay(this.flashObject, TODAY.plusYears(1)));
    }

    @Test
    void shouldDisplayWeeklyWithNonEnglishLocale()
    {
        when(this.flashObject.getIntValue("repeat")).thenReturn(1);
        when(this.flashObject.getStringValue("repeatFrequency")).thenReturn("1");
        when(this.flashObject.getDateValue("dateBegin")).thenReturn(toDate(LocalDate.of(2026, 10, 14)));
        when(this.flashObject.getStringValue("repeatInterval")).thenReturn("weekly");
        when(this.flashObject.getListValue("repeatDays")).thenReturn(List.of("wednesday"));

        // The repeat days are stored in English, they must not be compared with "mercredi" (e.g. with ?language=fr).
        when(this.xcontext.getLocale()).thenReturn(Locale.FRANCE);
        Locale defaultLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.FRANCE);
            assertTrue(this.manager.shouldDisplay(this.flashObject, TODAY));
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }

    @Test
    void shouldDisplayOnOtherDaysAndFrequencies()
    {
        when(this.flashObject.getIntValue("repeat")).thenReturn(1);

        // 2026-10-28 is a Wednesday, two weeks after the start.
        when(this.flashObject.getDateValue("dateBegin")).thenReturn(toDate(LocalDate.of(2026, 10, 14)));
        when(this.flashObject.getStringValue("repeatInterval")).thenReturn("weekly");
        when(this.flashObject.getStringValue("repeatFrequency")).thenReturn("2");
        when(this.flashObject.getListValue("repeatDays")).thenReturn(List.of("monday", "tuesday"));
        assertFalse(this.manager.shouldDisplay(this.flashObject, TODAY));

        // 48 months after the start, not a multiple of 9.
        when(this.flashObject.getDateValue("dateBegin")).thenReturn(toDate(LocalDate.of(2022, 10, 28)));
        when(this.flashObject.getStringValue("repeatInterval")).thenReturn("monthly");
        when(this.flashObject.getStringValue("repeatFrequency")).thenReturn("9");
        assertFalse(this.manager.shouldDisplay(this.flashObject, TODAY));
        when(this.flashObject.getStringValue("repeatFrequency")).thenReturn("6");
        assertTrue(this.manager.shouldDisplay(this.flashObject, TODAY));

        // 4 years after the start, not a multiple of 3.
        when(this.flashObject.getStringValue("repeatInterval")).thenReturn("yearly");
        when(this.flashObject.getStringValue("repeatFrequency")).thenReturn("3");
        assertFalse(this.manager.shouldDisplay(this.flashObject, TODAY));
    }

    @Test
    void shouldDisplayOnDaysMissingFromSomeMonthsOrYears()
    {
        when(this.flashObject.getIntValue("repeat")).thenReturn(1);
        when(this.flashObject.getStringValue("repeatFrequency")).thenReturn("1");

        // Monthly messages starting on the 31st are shown only in months with 31 days.
        when(this.flashObject.getDateValue("dateBegin")).thenReturn(toDate(LocalDate.of(2026, 1, 31)));
        when(this.flashObject.getStringValue("repeatInterval")).thenReturn("monthly");
        assertTrue(this.manager.shouldDisplay(this.flashObject, LocalDate.of(2026, 3, 31)));
        assertFalse(this.manager.shouldDisplay(this.flashObject, LocalDate.of(2026, 4, 30)));

        // Yearly messages starting on February 29th are shown only in leap years.
        when(this.flashObject.getDateValue("dateBegin")).thenReturn(toDate(LocalDate.of(2024, 2, 29)));
        when(this.flashObject.getStringValue("repeatInterval")).thenReturn("yearly");
        assertFalse(this.manager.shouldDisplay(this.flashObject, LocalDate.of(2025, 2, 28)));
        assertTrue(this.manager.shouldDisplay(this.flashObject, LocalDate.of(2028, 2, 29)));
    }

    @Test
    void shouldDisplayWithoutValidFrequency()
    {
        when(this.flashObject.getIntValue("repeat")).thenReturn(1);
        when(this.flashObject.getDateValue("dateBegin")).thenReturn(toDate(LocalDate.of(2026, 10, 20)));
        when(this.flashObject.getStringValue("repeatInterval")).thenReturn("daily");

        // Backwards compatibility: repeating messages without a valid frequency were never displayed.
        when(this.flashObject.getStringValue("repeatFrequency")).thenReturn("");
        assertFalse(this.manager.shouldDisplay(this.flashObject, TODAY));
        when(this.flashObject.getStringValue("repeatFrequency")).thenReturn("0");
        assertFalse(this.manager.shouldDisplay(this.flashObject, TODAY));
        when(this.flashObject.getStringValue("repeatFrequency")).thenReturn("abc");
        assertFalse(this.manager.shouldDisplay(this.flashObject, TODAY));
    }

    @Test
    void needsConfirmation() throws Exception
    {
        when(this.authorization.hasAccess(Right.VIEW, USER_REFERENCE, MESSAGE_REFERENCE)).thenReturn(true);
        assertTrue(this.manager.needsConfirmation(MESSAGE_REFERENCE));

        when(this.validationObject.getListValue("flash")).thenReturn(List.of("XWiki.Alice"));
        assertFalse(this.manager.needsConfirmation(MESSAGE_REFERENCE));

        // Repeating messages need a new confirmation each day.
        when(this.flashObject.getIntValue("repeat")).thenReturn(1);
        assertTrue(this.manager.needsConfirmation(MESSAGE_REFERENCE));
        when(this.validationObject.getDateValue("seenDate")).thenReturn(new Date());
        assertFalse(this.manager.needsConfirmation(MESSAGE_REFERENCE));
    }

    @Test
    void needsConfirmationWithoutViewRight() throws Exception
    {
        // Never confirmed, but the current user is not allowed to view the message.
        assertFalse(this.manager.needsConfirmation(MESSAGE_REFERENCE));

        verify(this.xwiki, never()).getDocument(MESSAGE_REFERENCE, this.xcontext);
    }

    @Test
    void markAsSeen() throws Exception
    {
        when(this.validationObject.getListValue("flash")).thenReturn(List.of("XWiki.Bob"));

        assertTrue(this.manager.markAsSeen(MESSAGE_REFERENCE));

        verify(this.validationObject).set("flash", List.of("XWiki.Bob", "XWiki.Alice"), this.xcontext);
        verify(this.validationObject).set(eq("seenDate"), any(Date.class), eq(this.xcontext));
        verify(this.xwiki).saveDocument(this.document, "XWiki.Alice has seen the message", true, this.xcontext);
    }

    @Test
    void markAsSeenWhenAlreadySeenToday() throws Exception
    {
        when(this.validationObject.getListValue("flash")).thenReturn(List.of("XWiki.Alice"));
        when(this.validationObject.getDateValue("seenDate")).thenReturn(new Date());

        assertTrue(this.manager.markAsSeen(MESSAGE_REFERENCE));

        verify(this.xwiki, never()).saveDocument(any(), anyString(), anyBoolean(), any());
    }

    @Test
    void markAsSeenWhenNotAFlashMessage() throws Exception
    {
        when(this.document.getXObject(FLASH_CLASS)).thenReturn(null);

        assertFalse(this.manager.markAsSeen(MESSAGE_REFERENCE));

        verify(this.xwiki, never()).saveDocument(any(), anyString(), anyBoolean(), any());
    }

    private SolrDocument solrDocument(DocumentReference reference)
    {
        SolrDocument solrDocument = new SolrDocument();
        solrDocument.setField("id", reference + "_" + reference.getLocale());
        when(this.solrDocumentResolver.resolve(solrDocument)).thenReturn(reference);
        return solrDocument;
    }

    private Date toDate(LocalDate date)
    {
        return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}
