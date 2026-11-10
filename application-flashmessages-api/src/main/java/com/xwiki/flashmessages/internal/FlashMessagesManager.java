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
import java.time.MonthDay;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrDocument;
import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;
import org.xwiki.security.authorization.AuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.user.UserReferenceResolver;
import org.xwiki.user.group.GroupException;
import org.xwiki.user.group.GroupManager;
import org.xwiki.user.group.WikiTarget;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

/**
 * Holds the flash messages logic: which messages the current user should see and whether they confirmed them.
 *
 * @version $Id$
 * @since 1.11.0
 */
@Component(roles = FlashMessagesManager.class)
@Singleton
public class FlashMessagesManager
{
    private static final String FLASH_SPACE = "Flash";

    private static final LocalDocumentReference FLASH_CLASS = new LocalDocumentReference(FLASH_SPACE, "FlashClass");

    private static final LocalDocumentReference VALIDATION_CLASS =
        new LocalDocumentReference(FLASH_SPACE, "FlashValidationClass");

    private static final String DATE_BEGIN = "dateBegin";

    private static final String REPEAT = "repeat";

    private static final String SEEN_BY = "flash";

    private static final String SEEN_DATE = "seenDate";

    private static final String GROUPS_FIELD = "property.Flash.FlashClass.groups_string";

    private static final String MESSAGES_QUERY = "object:Flash.FlashClass"
        + " AND property.Flash.FlashClass.dateBegin:[* TO NOW] AND -property.Flash.FlashClass.dateEnd:[* TO NOW]"
        + " AND (wiki:%s OR property.Flash.FlashClass.wikiScope:allWikis)";

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Inject
    private GroupManager groupManager;

    @Inject
    private QueryManager queryManager;

    @Inject
    private AuthorizationManager authorization;

    @Inject
    private DocumentReferenceResolver<SolrDocument> solrDocumentResolver;

    @Inject
    private EntityReferenceSerializer<String> serializer;

    @Inject
    @Named("local")
    private EntityReferenceSerializer<String> localSerializer;

    @Inject
    @Named("compactwiki")
    private EntityReferenceSerializer<String> compactWikiSerializer;

    @Inject
    @Named("document")
    private UserReferenceResolver<DocumentReference> userReferenceResolver;

    /**
     * @return the flash messages scheduled today for the groups of the current user, most recent first
     * @throws GroupException when failing to get the groups of the current user
     * @throws QueryException when failing to search for flash messages
     * @throws XWikiException when failing to load a flash message
     */
    public List<DocumentReference> getMessages() throws GroupException, QueryException, XWikiException
    {
        XWikiContext xcontext = this.xcontextProvider.get();
        DocumentReference user = xcontext.getUserReference();
        if (user == null) {
            return List.of();
        }

        // Global users can be in groups from any wiki (null target), local users only in groups from their wiki.
        // This follows the logic from the Groups user profile section (see XWiki.XWikiUserMembershipSheet).
        Collection<DocumentReference> groups = this.groupManager.getGroups(user,
            xcontext.isMainWiki(user.getWikiReference().getName()) ? null : WikiTarget.ENTITY, true);
        if (groups.isEmpty()) {
            return List.of();
        }

        Query query = this.queryManager.createQuery(
            String.format(MESSAGES_QUERY, ClientUtils.escapeQueryChars(xcontext.getWikiId())), "solr");
        query.bindValue("fq", String.format("type:DOCUMENT AND -fullname:Flash.FlashTemplate AND (%s)",
            groups.stream().map(this::getGroupFilter).collect(Collectors.joining(" OR "))));
        query.setLimit(300);
        QueryResponse response = (QueryResponse) query.execute().get(0);

        LocalDate today = LocalDate.now();
        List<BaseObject> messages = new ArrayList<>();
        Set<DocumentReference> references = new HashSet<>();
        for (SolrDocument result : response.getResults()) {
            // Each translation is indexed separately, but only the default one holds the objects.
            DocumentReference reference = this.solrDocumentResolver.resolve(result).withoutLocale();
            // Skip the other translations of a message already handled and the messages the current user is not
            // allowed to view.
            if (!references.add(reference) || !this.authorization.hasAccess(Right.VIEW, user, reference)) {
                continue;
            }
            // Skip the messages deleted since Solr indexed them.
            BaseObject message = xcontext.getWiki().getDocument(reference, xcontext).getXObject(FLASH_CLASS);
            if (message != null && message.getDateValue(DATE_BEGIN) != null && shouldDisplay(message, today)) {
                messages.add(message);
            }
        }
        messages.sort(Comparator.comparing((BaseObject message) -> message.getDateValue(DATE_BEGIN)).reversed());
        return messages.stream().map(BaseObject::getDocumentReference).collect(Collectors.toList());
    }

    /**
     * @param messageReference a flash message
     * @return {@code true} if the current user never confirmed the message or if it repeats and nobody confirmed it
     *     today
     * @throws XWikiException when failing to load the message or the current user
     */
    public boolean needsConfirmation(DocumentReference messageReference) throws XWikiException
    {
        XWikiContext xcontext = this.xcontextProvider.get();
        DocumentReference user = xcontext.getUserReference();
        if (user == null || !this.authorization.hasAccess(Right.VIEW, user, messageReference)) {
            return false;
        }
        XWikiDocument message = xcontext.getWiki().getDocument(messageReference, xcontext);
        BaseObject flashObject = message.getXObject(FLASH_CLASS);
        if (flashObject == null) {
            return false;
        }

        BaseObject validationObject = message.getXObject(VALIDATION_CLASS);
        // Old versions stored the seen messages in the user profile.
        BaseObject legacyValidationObject = xcontext.getWiki().getDocument(user, xcontext).getXObject(VALIDATION_CLASS);
        boolean seen = getSeenBy(validationObject).contains(getUser(user, messageReference))
            || getSeenBy(legacyValidationObject).contains(this.serializer.serialize(messageReference));
        return !seen || (flashObject.getIntValue(REPEAT) == 1 && !isSeenToday(validationObject));
    }

    /**
     * Mark a flash message as seen by the current user.
     *
     * @param messageReference the flash message
     * @return {@code false} if the document is not a flash message
     * @throws XWikiException when failing to load or save the flash message
     */
    public boolean markAsSeen(DocumentReference messageReference) throws XWikiException
    {
        XWikiContext xcontext = this.xcontextProvider.get();
        XWikiDocument document = xcontext.getWiki().getDocument(messageReference, xcontext);
        if (document.getXObject(FLASH_CLASS) == null) {
            return false;
        }

        document = document.clone();
        BaseObject validationObject = document.getXObject(VALIDATION_CLASS, true, xcontext);
        String user = getUser(xcontext.getUserReference(), messageReference);
        List<Object> seenBy = new ArrayList<>(getSeenBy(validationObject));
        if (!seenBy.contains(user)) {
            seenBy.add(user);
        } else if (isSeenToday(validationObject)) {
            // Nothing to update, avoid creating a new document version.
            return true;
        }

        validationObject.set(SEEN_BY, seenBy, xcontext);
        validationObject.set(SEEN_DATE, new Date(), xcontext);
        document.getAuthors().setOriginalMetadataAuthor(
            this.userReferenceResolver.resolve(xcontext.getUserReference()));
        xcontext.getWiki().saveDocument(document, String.format("%s has seen the message", user), true, xcontext);
        return true;
    }

    /**
     * See if a given flashmessage should be displayed on a given date.
     *
     * @param message the {@code Flash.FlashClass} object of a message
     * @param today the current date
     * @return {@code true} if the message should be displayed in the slider
     */
    public boolean shouldDisplay(BaseObject message, LocalDate today)
    {
        if (message.getIntValue(REPEAT) != 1) {
            return true;
        }

        LocalDate begin = message.getDateValue(DATE_BEGIN).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        ChronoUnit unit;
        boolean matchingDay;
        switch (message.getStringValue("repeatInterval")) {
            case "daily":
                unit = ChronoUnit.DAYS;
                matchingDay = true;
                break;
            case "weekly":
                unit = ChronoUnit.WEEKS;
                matchingDay = message.getListValue("repeatDays")
                    .contains(today.getDayOfWeek().name().toLowerCase(Locale.ROOT));
                break;
            case "monthly":
                unit = ChronoUnit.MONTHS;
                matchingDay = begin.getDayOfMonth() == today.getDayOfMonth();
                break;
            case "yearly":
                unit = ChronoUnit.YEARS;
                matchingDay = MonthDay.from(begin).equals(MonthDay.from(today));
                break;
            default:
                return false;
        }
        // A missing or invalid frequency never matches.
        int frequency = NumberUtils.toInt(message.getStringValue("repeatFrequency"));
        return matchingDay && frequency > 0 && unit.between(begin, today) % frequency == 0;
    }

    private String getGroupFilter(DocumentReference group)
    {
        // Messages store the groups relative to their own wiki.
        String wiki = ClientUtils.escapeQueryChars(group.getWikiReference().getName());
        return String.format("(wiki:%1$s AND %2$s:%3$s) OR (-wiki:%1$s AND %2$s:%4$s)", wiki, GROUPS_FIELD,
            ClientUtils.escapeQueryChars(this.localSerializer.serialize(group)),
            ClientUtils.escapeQueryChars(this.serializer.serialize(group)));
    }

    private String getUser(DocumentReference user, DocumentReference messageReference)
    {
        // Users are stored relative to the wiki of the message.
        return this.compactWikiSerializer.serialize(user, messageReference);
    }

    private List<?> getSeenBy(BaseObject validationObject)
    {
        return validationObject == null ? List.of() : validationObject.getListValue(SEEN_BY);
    }

    private boolean isSeenToday(BaseObject validationObject)
    {
        Date seenDate = validationObject == null ? null : validationObject.getDateValue(SEEN_DATE);
        return seenDate != null && DateUtils.isSameDay(seenDate, new Date());
    }
}
